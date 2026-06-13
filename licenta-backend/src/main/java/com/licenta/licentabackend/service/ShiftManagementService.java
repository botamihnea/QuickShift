package com.licenta.licentabackend.service;

import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.LeaveRequest;
import com.licenta.licentabackend.domain.Notification;
import com.licenta.licentabackend.domain.Shift;
import com.licenta.licentabackend.dto.EmployeeTracker;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.LeaveRequestRepository;
import com.licenta.licentabackend.repository.NotificationRepository;
import com.licenta.licentabackend.repository.ShiftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ShiftManagementService {

    private static final Logger log = LoggerFactory.getLogger(ShiftManagementService.class);

    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final NotificationRepository notificationRepository;

    public ShiftManagementService(
            EmployeeRepository employeeRepository,
            ShiftRepository shiftRepository,
            LeaveRequestRepository leaveRequestRepository,
            NotificationRepository notificationRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.notificationRepository = notificationRepository;
    }

    public List<Employee> getEligibleEmployees(Long storeId, LocalDate shiftDate, String shiftType) {
        List<Employee> allStoreEmployees = employeeRepository.findByStoreId(storeId);
        if (allStoreEmployees.isEmpty()) {
            log.debug("No employees found for store {} — returning empty eligibility list", storeId);
            return List.of();
        }

        LocalDate monthStart = shiftDate.withDayOfMonth(1);
        List<Shift> existingShifts = shiftRepository.findByEmployeeStoreIdAndShiftDateBetween(
                storeId, monthStart, shiftDate
        );

        Map<Long, EmployeeTracker> trackerMap = new HashMap<>();
        for (Employee emp : allStoreEmployees) {
            trackerMap.put(emp.getId(), new EmployeeTracker(emp));
        }

        LocalDate cursor = monthStart;
        while (!cursor.isAfter(shiftDate)) {
            LocalDate day = cursor;

            if (day.getDayOfWeek() == DayOfWeek.MONDAY) {
                trackerMap.values().forEach(EmployeeTracker::resetWeeklyHours);
            }

            Set<Long> workedToday = existingShifts.stream()
                    .filter(s -> s.getShiftDate().equals(day))
                    .map(s -> s.getEmployee().getId())
                    .collect(Collectors.toSet());

            for (EmployeeTracker tracker : trackerMap.values()) {
                boolean worked = workedToday.contains(tracker.getEmployee().getId());
                if (worked) {
                    int hours = existingShifts.stream()
                            .filter(s -> s.getShiftDate().equals(day)
                                    && s.getEmployee().getId().equals(tracker.getEmployee().getId()))
                            .mapToInt(s -> parseHoursFromShiftType(s.getShiftType()))
                            .sum();
                    tracker.assignShift(hours);
                } else {
                    tracker.registerDayOff();
                }
            }

            cursor = cursor.plusDays(1);
        }

        Set<Long> alreadyWorkingThatDay = existingShifts.stream()
                .filter(s -> s.getShiftDate().equals(shiftDate))
                .map(s -> s.getEmployee().getId())
                .collect(Collectors.toSet());

        boolean isMorning = isMorningShift(shiftType);
        int requiredHours = parseHoursFromShiftType(shiftType);
        String requiredContract = hoursToContractType(requiredHours);

        Set<Long> employeesOnLeave = leaveRequestRepository
                .findByStatusAndRequestingEmployeeStoreIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        "APPROVED",
                        storeId,
                        shiftDate,
                        shiftDate
                )
                .stream()
                .map(LeaveRequest::getRequestingEmployee)
                .map(Employee::getId)
                .collect(Collectors.toSet());

        List<EmployeeTracker> eligible = trackerMap.values().stream()
                .filter(t -> t.getConsecutiveWorkedDays() < 5)
                .filter(t -> !t.isHad12HourShiftYesterday())
                .filter(t -> !alreadyWorkingThatDay.contains(t.getEmployee().getId()))
                .filter(t -> !employeesOnLeave.contains(t.getEmployee().getId()))
                .filter(t -> canWorkMoreHoursThisWeek(t, requiredHours))
                .filter(t -> contractMatches(t.getEmployee().getContractType(), requiredContract))
                .collect(Collectors.toList());

        eligible.sort(Comparator
                .comparingInt((EmployeeTracker t) -> preferenceScore(t.getEmployee().getShiftPreference(), isMorning))
                .thenComparingInt(EmployeeTracker::getWorkedHoursCurrentMonth));

        log.info("Eligibility check: store={}, date={}, shiftType={} → {} eligible employees",
                storeId, shiftDate, shiftType, eligible.size());
        return eligible.stream().map(EmployeeTracker::getEmployee).toList();
    }

    @Transactional
    public Shift createManualShift(Long storeId, Long employeeId, LocalDate shiftDate, String shiftType) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found."));

        if (employee.getStore() == null || !employee.getStore().getId().equals(storeId)) {
            throw new IllegalArgumentException("Employee is not assigned to this store.");
        }

        List<Employee> eligible = getEligibleEmployees(storeId, shiftDate, shiftType);
        boolean isEligible = eligible.stream().anyMatch(emp -> emp.getId().equals(employeeId));
        if (!isEligible) {
            throw new IllegalArgumentException("Selected employee is not eligible for this shift.");
        }

        boolean alreadyScheduled = shiftRepository
                .findByEmployeeIdAndShiftDateBetween(employeeId, shiftDate, shiftDate)
                .stream()
                .anyMatch(shift -> shift.getShiftDate().equals(shiftDate));
        if (alreadyScheduled) {
            throw new IllegalArgumentException("Employee already has a shift on this date.");
        }

        Shift shift = new Shift();
        shift.setEmployee(employee);
        shift.setShiftDate(shiftDate);
        shift.setShiftType(shiftType);
        shift.setStatus("SCHEDULED");
        Shift saved = shiftRepository.save(shift);

        log.info("Manual shift created: id={}, employee={} ({}), date={}, type={}",
                saved.getId(), employee.getFullName(), employee.getId(), shiftDate, shiftType);

        if (employee.getAppUser() != null) {
            String message = String.format(
                    "[NEW SHIFT] You were assigned a new shift on %s (%s).",
                    shiftDate, shiftType
            );
            notificationRepository.save(new Notification(message, employee.getAppUser(), employee.getStore()));
        }

        return saved;
    }

    @Transactional
    public void deleteShift(Long storeId, Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found."));

        if (shift.getEmployee().getStore() == null || !shift.getEmployee().getStore().getId().equals(storeId)) {
            throw new IllegalArgumentException("Shift does not belong to your store.");
        }

        shiftRepository.delete(shift);
        log.info("Shift deleted: id={}, employee={}, date={}, type={}",
                shiftId, shift.getEmployee().getFullName(), shift.getShiftDate(), shift.getShiftType());

        if (shift.getEmployee().getAppUser() != null) {
            String message = String.format(
                    "[SHIFT REMOVED] Your shift on %s (%s) was removed by your manager.",
                    shift.getShiftDate(), shift.getShiftType()
            );
            notificationRepository.save(new Notification(message, shift.getEmployee().getAppUser(), shift.getEmployee().getStore()));
        }
    }

    private int parseHoursFromShiftType(String shiftType) {
        if (shiftType == null) {
            return 8;
        }
        if (shiftType.contains("FULL_TIME") || shiftType.startsWith("SHIFT_")) {
            return 8;
        }
        if (shiftType.contains("PART_TIME_6") || shiftType.contains("10_16") || shiftType.contains("16_22")) {
            return 6;
        }
        if (shiftType.contains("PART_TIME_4") || shiftType.contains("10_14") || shiftType.contains("16_20")) {
            return 4;
        }
        return 8;
    }

    private String hoursToContractType(int hours) {
        return switch (hours) {
            case 4 -> "PART_TIME_4H";
            case 6 -> "PART_TIME_6H";
            default -> "FULL_TIME_8H";
        };
    }

    private boolean contractMatches(String employeeContract, String requiredContract) {
        if (employeeContract == null) {
            return false;
        }
        if ("FULL_TIME_8H".equals(employeeContract)) {
            return true;
        }
        return employeeContract.equals(requiredContract);
    }

    private boolean isMorningShift(String shiftType) {
        if (shiftType == null) {
            return true;
        }
        return !shiftType.endsWith("_20") && !shiftType.endsWith("_22");
    }

    private int preferenceScore(String preference, boolean isMorning) {
        if (preference == null || preference.isBlank() || "ANY".equalsIgnoreCase(preference)) {
            return 1;
        }
        if (isMorning && "MORNING".equalsIgnoreCase(preference)) {
            return 0;
        }
        if (!isMorning && "EVENING".equalsIgnoreCase(preference)) {
            return 0;
        }
        return 2;
    }

    private boolean canWorkMoreHoursThisWeek(EmployeeTracker tracker, int incomingHours) {
        int futureHours = tracker.getWorkedHoursCurrentWeek() + incomingHours;
        String contract = tracker.getEmployee().getContractType();
        if (contract == null) {
            return false;
        }
        return switch (contract) {
            case "FULL_TIME_8H" -> futureHours <= 48;
            case "PART_TIME_6H" -> futureHours <= 30;
            case "PART_TIME_4H" -> futureHours <= 20;
            default -> false;
        };
    }
}
