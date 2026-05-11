package com.licenta.licentabackend.service;

import com.licenta.licentabackend.domain.*;
import com.licenta.licentabackend.dto.AcknowledgeAbsenceResponse;
import com.licenta.licentabackend.dto.EmployeeTracker;
import com.licenta.licentabackend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AbsenceService {

    private static final Logger log = LoggerFactory.getLogger(AbsenceService.class);

    private final AbsenceRequestRepository absenceRequestRepository;
    private final ShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public AbsenceService(
            AbsenceRequestRepository absenceRequestRepository,
            ShiftRepository shiftRepository,
            EmployeeRepository employeeRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository) {
        this.absenceRequestRepository = absenceRequestRepository;
        this.shiftRepository = shiftRepository;
        this.employeeRepository = employeeRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // EMPLOYEE ACTION: report that they cannot attend a future shift
    // -------------------------------------------------------------------------
    @Transactional
    public void reportAbsence(Long shiftId, String reason, AppUser currentUser) {

        // 1. Resolve the employee profile for the logged-in user
        Employee employee = employeeRepository.findByAppUserId(currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Employee profile not found for this user."));

        // 2. Load the shift and verify ownership
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found."));

        if (!shift.getEmployee().getId().equals(employee.getId())) {
            throw new IllegalArgumentException("This shift does not belong to you.");
        }

        // 3. Only allow reporting for future shifts
        if (!shift.getShiftDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("You can only report absence for future shifts.");
        }

        // 4. Check that the shift is still in a reportable status
        // Both SCHEDULED and REPLACEMENT shifts can be reported absent
        if (!"SCHEDULED".equals(shift.getStatus()) && !"REPLACEMENT".equals(shift.getStatus())) {
            throw new IllegalArgumentException("This shift has already been reported or processed.");
        }

        // 5. Guard: no duplicate pending requests for the same shift
        boolean alreadyRequested = absenceRequestRepository
                .findByShiftIdAndStatusIn(shiftId, List.of("PENDING"))
                .isPresent();
        if (alreadyRequested) {
            throw new IllegalArgumentException("An absence request for this shift is already pending.");
        }

        // 6. Create the absence request
        AbsenceRequest absenceRequest = new AbsenceRequest(shift, employee, reason);
        AbsenceRequest saved = absenceRequestRepository.save(absenceRequest);

        // 7. Notify the store manager — only the manager of this exact store
        Store store = employee.getStore();
        if (store == null) {
            log.warn("Employee {} has no store assigned, cannot notify manager.", employee.getId());
            return;
        }

        List<AppUser> managers = userRepository.findByStoreIdAndRole(store.getId(), Role.MANAGER);
        if (managers.isEmpty()) {
            log.warn("No manager found for store {}, absence request saved but no notification sent.", store.getId());
            return;
        }

        String shiftLabel = shift.getShiftDate() + " (" + shift.getShiftType() + ")";
        String notificationMessage = String.format(
                "[ABSENCE REQUEST] %s cannot attend their shift on %s. Please acknowledge to find a replacement.",
                employee.getFullName(), shiftLabel);

        // Send notification only to the manager of this store (findByStoreIdAndRole
        // already scopes it)
        for (AppUser manager : managers) {
            Notification notification = new Notification(
                    notificationMessage,
                    manager,
                    store,
                    saved.getId() // links the notification to the absence request
            );
            notificationRepository.save(notification);
        }

        log.info("Absence request {} created for employee {} on shift {}", saved.getId(), employee.getId(), shiftId);
    }

    // -------------------------------------------------------------------------
    // MANAGER ACTION: acknowledge the absence and find the best replacement
    // -------------------------------------------------------------------------
    @Transactional
    public AcknowledgeAbsenceResponse acknowledgeAbsence(Long absenceRequestId, AppUser manager) {

        // 1. Load the absence request
        AbsenceRequest absenceRequest = absenceRequestRepository.findById(absenceRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Absence request not found."));

        if (!"PENDING".equals(absenceRequest.getStatus())) {
            throw new IllegalArgumentException("This absence request has already been processed.");
        }

        // 2. Verify the manager owns the store of the absent employee
        Store store = absenceRequest.getRequestingEmployee().getStore();
        if (store == null || !store.getId().equals(manager.getStore().getId())) {
            throw new IllegalArgumentException("You are not the manager of this store.");
        }

        // 3. Mark the original shift as ABSENT (visible on the main calendar)
        Shift absentShift = absenceRequest.getShift();
        absentShift.setStatus("ABSENT");
        shiftRepository.save(absentShift);

        LocalDate absenceDate = absentShift.getShiftDate();
        String shiftType = absentShift.getShiftType();

        // 4. Find the best replacement using CSP-compatible logic
        Employee replacement = findBestReplacement(absenceDate, shiftType, store.getId(),
                absenceRequest.getRequestingEmployee().getId());

        if (replacement != null) {
            // 5a. Insert replacement shift into the schedule (appears on the main calendar)
            Shift replacementShift = new Shift();
            replacementShift.setEmployee(replacement);
            replacementShift.setShiftDate(absenceDate);
            replacementShift.setShiftType(shiftType);
            replacementShift.setStatus("REPLACEMENT");
            shiftRepository.save(replacementShift);

            absenceRequest.setStatus("COVERED");

            // 6a. Notify the replacement employee
            AppUser replacementUser = replacement.getAppUser();
            if (replacementUser != null) {
                String replMsg = String.format(
                        "[NEW SHIFT] You have been assigned an extra shift on %s (%s) because a colleague cannot attend.",
                        absenceDate, shiftType);
                notificationRepository.save(new Notification(replMsg, replacementUser, store));
            }

            // 6b. Confirm to manager
            String confirmMsg = String.format(
                    "[COVERED] Absence acknowledged. %s will cover the %s (%s) shift.",
                    replacement.getFullName(), absenceDate, shiftType);
            notificationRepository.save(new Notification(confirmMsg, manager, store));

            log.info("Replacement {} assigned to shift on {} for store {}", replacement.getId(), absenceDate,
                    store.getId());
            absenceRequestRepository.save(absenceRequest);
            return new AcknowledgeAbsenceResponse(true, replacement.getFullName());

        } else {
            // 5b. No replacement found
            absenceRequest.setStatus("UNRESOLVABLE");

            String noReplMsg = String.format(
                    "[NO REPLACEMENT] No available replacement found for the %s (%s) shift. Manual intervention required.",
                    absenceDate, shiftType);
            notificationRepository.save(new Notification(noReplMsg, manager, store));

            log.warn("No replacement found for shift on {} in store {}", absenceDate, store.getId());
            absenceRequestRepository.save(absenceRequest);
            return new AcknowledgeAbsenceResponse(false, null);
        }
    }

    // -------------------------------------------------------------------------
    // CSP replacement finder — reconstructs tracker state from real DB data
    // -------------------------------------------------------------------------
    private Employee findBestReplacement(LocalDate absenceDate, String absentShiftType,
            Long storeId, Long absentEmployeeId) {

        // Load all store employees
        List<Employee> allStoreEmployees = employeeRepository.findByStoreId(storeId);

        // Exclude the absent employee themselves
        List<Employee> candidates = allStoreEmployees.stream()
                .filter(e -> !e.getId().equals(absentEmployeeId))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }

        // Load all shifts for the store from the start of that month up to (and
        // including) the absence date
        LocalDate monthStart = absenceDate.withDayOfMonth(1);
        List<Shift> existingShifts = shiftRepository.findByEmployeeStoreIdAndShiftDateBetween(
                storeId, monthStart, absenceDate);

        // Build a map of EmployeeTracker per employee — replay the schedule to get
        // accurate state
        Map<Long, EmployeeTracker> trackerMap = new HashMap<>();
        for (Employee emp : candidates) {
            trackerMap.put(emp.getId(), new EmployeeTracker(emp));
        }

        // Replay all days from month start to absence date to reconstruct
        // consecutive/weekly state
        LocalDate cursor = monthStart;
        while (!cursor.isAfter(absenceDate)) {
            final LocalDate day = cursor;

            // Reset weekly hours every Monday
            if (day.getDayOfWeek() == DayOfWeek.MONDAY) {
                trackerMap.values().forEach(EmployeeTracker::resetWeeklyHours);
            }

            // Collect which employees worked on this day
            Set<Long> workedToday = existingShifts.stream()
                    .filter(s -> s.getShiftDate().equals(day))
                    .map(s -> s.getEmployee().getId())
                    .collect(Collectors.toSet());

            for (EmployeeTracker tracker : trackerMap.values()) {
                boolean worked = workedToday.contains(tracker.getEmployee().getId());
                // Determine shift hours for the worked day
                int hours = 0;
                if (worked) {
                    hours = existingShifts.stream()
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

        // Who is already working on the absence day (before we add the replacement)?
        Set<Long> alreadyWorkingThatDay = existingShifts.stream()
                .filter(s -> s.getShiftDate().equals(absenceDate))
                .map(s -> s.getEmployee().getId())
                .collect(Collectors.toSet());

        // Determine if the absent shift is a morning or evening slot
        boolean isMorning = isMorningShift(absentShiftType);

        // Determine the contract type the shift requires
        int requiredHours = parseHoursFromShiftType(absentShiftType);
        String requiredContract = hoursToContractType(requiredHours);

        // Filter eligible candidates using the same CSP rules as SchedulingService
        List<EmployeeTracker> eligible = trackerMap.values().stream()
                .filter(t -> t.getConsecutiveWorkedDays() < 5) // hard rule: max 5 consecutive days
                .filter(t -> !t.isHad12HourShiftYesterday()) // hard rule: no 12h back-to-back
                .filter(t -> !alreadyWorkingThatDay.contains(t.getEmployee().getId())) // not already scheduled
                .filter(t -> canWorkMoreHoursThisWeek(t, requiredHours)) // weekly hour cap
                .filter(t -> contractMatches(t.getEmployee().getContractType(), requiredContract)) // contract compat
                .collect(Collectors.toList());

        if (eligible.isEmpty()) {
            return null;
        }

        // Sort: prefer shift-preference match first, then fewest hours worked this
        // month
        eligible.sort(Comparator
                .comparingInt((EmployeeTracker t) -> preferenceScore(t.getEmployee().getShiftPreference(), isMorning))
                .thenComparingInt(EmployeeTracker::getWorkedHoursCurrentMonth));

        return eligible.get(0).getEmployee();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int parseHoursFromShiftType(String shiftType) {
        if (shiftType == null)
            return 8;
        if (shiftType.contains("FULL_TIME") || shiftType.startsWith("SHIFT_"))
            return 8;
        if (shiftType.contains("PART_TIME_6") || shiftType.contains("10_16") || shiftType.contains("16_22"))
            return 6;
        if (shiftType.contains("PART_TIME_4") || shiftType.contains("10_14") || shiftType.contains("16_20"))
            return 4;
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
        if (employeeContract == null)
            return false;
        // Full-time employees can cover any slot; part-time only cover their own
        // duration
        if ("FULL_TIME_8H".equals(employeeContract))
            return true;
        return employeeContract.equals(requiredContract);
    }

    private boolean isMorningShift(String shiftType) {
        if (shiftType == null)
            return true;
        // Evening shifts end at 20, 22
        return !shiftType.endsWith("_20") && !shiftType.endsWith("_22");
    }

    private int preferenceScore(String preference, boolean isMorning) {
        if (preference == null || preference.isBlank() || "ANY".equalsIgnoreCase(preference))
            return 1;
        if (isMorning && "MORNING".equalsIgnoreCase(preference))
            return 0;
        if (!isMorning && "EVENING".equalsIgnoreCase(preference))
            return 0;
        return 2; // wrong preference — last resort
    }

    private boolean canWorkMoreHoursThisWeek(EmployeeTracker tracker, int incomingHours) {
        int futureHours = tracker.getWorkedHoursCurrentWeek() + incomingHours;
        String contract = tracker.getEmployee().getContractType();
        if (contract == null)
            return false;
        return switch (contract) {
            case "FULL_TIME_8H" -> futureHours <= 48;
            case "PART_TIME_6H" -> futureHours <= 30;
            case "PART_TIME_4H" -> futureHours <= 20;
            default -> false;
        };
    }
}
