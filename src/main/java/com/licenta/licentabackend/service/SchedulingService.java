package com.licenta.licentabackend.service;

import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Shift;
import com.licenta.licentabackend.dto.DayForecastDto;
import com.licenta.licentabackend.dto.EmployeeTracker;
import com.licenta.licentabackend.exceptions.NoEmployeesException;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SchedulingService {
    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);
    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;

    public SchedulingService(EmployeeRepository employeeRepository, ShiftRepository shiftRepository) {
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
    }

    @Transactional
    public void generateSchedule(List<DayForecastDto> forecastDays) {
        log.info("Initializing Heuristic CSP Solver...");
        List<Employee> allEmployees = employeeRepository.findAll();
        if (allEmployees.isEmpty()) {
            throw new NoEmployeesException("No employees found in the database!");
        }
        List<EmployeeTracker> trackers = allEmployees.stream()
                .map(EmployeeTracker::new)
                .collect(Collectors.toList());

        List<Shift> shiftsToSave = new ArrayList<>();

        for (int d = 0; d < forecastDays.size(); d++) {

            DayForecastDto dayForecast = forecastDays.get(d);

            if (dayForecast.getDate().getDayOfWeek() == DayOfWeek.MONDAY) {
                trackers.forEach(EmployeeTracker::resetWeeklyHours);
            }

            int targetMorning = 1;
            int targetEvening = 1;

            DayOfWeek dow = dayForecast.getDate().getDayOfWeek();
            boolean isThursdayToSunday = (dow == DayOfWeek.SATURDAY) || (dow == DayOfWeek.SUNDAY) ||
                    (dow == DayOfWeek.FRIDAY) || (dow == DayOfWeek.THURSDAY);

            boolean isReceptionDay = dayForecast.getReceptionDay();
            boolean isDayBeforeReception = false;

            if (d + 1 < forecastDays.size()) {
                isDayBeforeReception = forecastDays.get(d + 1).getReceptionDay();
            }

            if (isThursdayToSunday || isReceptionDay || isDayBeforeReception) {
                targetMorning = 2;
                targetEvening = 2;
                log.info("HR Rule activated - we need at least 2 people on day {}.", dayForecast.getDate());
            }

            int projectedSale = dayForecast.getSalesForecast();

            if (projectedSale > 3000) {
                targetEvening++; // ZI AGLOMERATA - ADAUGAM INCA UN OM
                log.info("Big sales estimated ({}) for {}. Upped to {} people on the evening shift.",
                        projectedSale, dayForecast.getDate(), targetEvening);
            }
            if (projectedSale > 7000) {
                targetMorning++; // ZI EXCEPTIONALA - 1 MARTIE, 8 MARTIE , ETC
                log.info("!!! Massive sales forecast ({}) for {}. Upped to {} people on the morning shift.",
                        projectedSale, dayForecast.getDate(), targetMorning);
            }

            int totalMenRequired = targetMorning + targetEvening;
            int currentMorningCount = 0;
            int currentEveningCount = 0;

            List<EmployeeTracker> availableTrackers = trackers.stream()
                    .filter(t -> t.getConsecutiveWorkedDays() < 5)
                    .filter(t -> !t.isHad12HourShiftYesterday())
                    .collect(Collectors.toList());

            availableTrackers.sort(Comparator.comparingInt(EmployeeTracker::getWorkedHoursCurrentMonth));

            List<EmployeeTracker> assignedToday = new ArrayList<>();

            for (int man = 0; man < totalMenRequired; man++) {

                EmployeeTracker chosenTracker = null;
                int actualShiftDuration = 0;
                String actualShiftType = "";
                boolean countAsMorning = false;

                for (int tracker = 0; tracker < availableTrackers.size(); tracker++) {
                    EmployeeTracker candidate = availableTrackers.get(tracker);
                    String contract = candidate.getEmployee().getContractType();

                    int tempDuration = 0;
                    String tempType = "";
                    boolean isMorningCandidate = false;

                    if (contract != null && contract.equals("FULL_TIME_8H")) {
                        tempDuration = 8;
                        if (currentMorningCount < targetMorning) {
                            tempType = "SHIFT_1_10_18";
                            isMorningCandidate = true;
                        } else {
                            tempType = "SHIFT_2_14_22";
                            isMorningCandidate = false;
                        }
                    } else if (contract != null && contract.equals("PART_TIME_4H")) {
                        tempDuration = 4;
                        tempType = "PART_TIME_16_20";
                        isMorningCandidate = false;
                    }
                    else if (contract != null && contract.equals("PART_TIME_6H")) {
                        tempDuration = 6;
                        tempType = "PART_TIME_16_22";
                        isMorningCandidate = false;
                    }
                    if (canWorkMoreHoursThisWeek(candidate, tempDuration)) {
                        chosenTracker = candidate;
                        actualShiftDuration = tempDuration;
                        actualShiftType = tempType;
                        countAsMorning = isMorningCandidate;

                        availableTrackers.remove(tracker);
                        break;// we assign him to a shift once per day
                    }
                }
                if (chosenTracker == null) {
                    log.warn("Did not find employee on slot {} from {} necessary for day {}", man + 1, totalMenRequired, dayForecast.getDate());
                    break;
                }

                if (countAsMorning) {
                    currentMorningCount++;
                } else {
                    currentEveningCount++;
                }

                assignEmployeesToShift(shiftsToSave, assignedToday, actualShiftDuration, chosenTracker, dayForecast, actualShiftType);
            }

            for (EmployeeTracker tracker : trackers) {
                if (!assignedToday.contains(tracker)) {
                    tracker.registerDayOff();
                }
            }
        }

        shiftRepository.saveAll(shiftsToSave);

        log.info("✅ Successfully generated and saved {} shifts in the database!", shiftsToSave.size());
    }

    private boolean canWorkMoreHoursThisWeek(EmployeeTracker tracker, int incomingHoursOnShift) {
        int futureHours = tracker.getWorkedHoursCurrentWeek() + incomingHoursOnShift;
        String contract = tracker.getEmployee().getContractType();
        if (contract != null) {
            if (contract.equals("FULL_TIME_8H")) {
                return futureHours <= 48;
            }
            if (contract.equals("PART_TIME_6H")) {
                return futureHours <= 30;
            }
            if (contract.equals("PART_TIME_4H")) {
                return futureHours < 20;
            }
        }
        return false;
    }

    private void assignEmployeesToShift(List<Shift> shiftsToSave, List<EmployeeTracker> assignedToday, int actualShiftDuration, EmployeeTracker chosenTracker, DayForecastDto dayForecast, String actualShiftType) {
        chosenTracker.assignShift(actualShiftDuration);
        assignedToday.add(chosenTracker);

        Shift shift = new Shift();

        shift.setEmployee(chosenTracker.getEmployee());
        shift.setShiftType(actualShiftType);
        shift.setShiftDate(dayForecast.getDate());
        shiftsToSave.add(shift);
    }

}
