package com.licenta.licentabackend.service;

import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Shift;
import com.licenta.licentabackend.domain.Store;
import com.licenta.licentabackend.dto.DayForecastDto;
import com.licenta.licentabackend.dto.EmployeeTracker;
import com.licenta.licentabackend.dto.GenerateScheduleResponseDto;
import com.licenta.licentabackend.exceptions.NoEmployeesException;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.ShiftRepository;
import com.licenta.licentabackend.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SchedulingService {
    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);
    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;
    private final StoreRepository storeRepository;
    private final CsvReaderService csvReaderService;
    private final String forecastCsvPath;
    int BIG_SALES_THRESHOLD = 2000;
    int MASSIVE_SALES_THRESHOLD = 5000;

    public SchedulingService(EmployeeRepository employeeRepository,
                             ShiftRepository shiftRepository,
                             StoreRepository storeRepository,
                             CsvReaderService csvReaderService,
                             @Value("${app.forecast.csv-path:}") String forecastCsvPath) {
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
        this.storeRepository = storeRepository;
        this.csvReaderService = csvReaderService;
        this.forecastCsvPath = forecastCsvPath;
    }

    @Transactional
    public GenerateScheduleResponseDto generateScheduleForMonth(Integer year, Integer month) {
        return generateScheduleForMonth(year, month, null);
    }

    @Transactional
    public GenerateScheduleResponseDto generateScheduleForMonth(Integer year, Integer month, Long storeId) {
        YearMonth targetMonth = resolveTargetMonth(year, month);

        if (forecastCsvPath == null || forecastCsvPath.isBlank()) {
            throw new IllegalArgumentException("Forecast file path is not configured. Please set app.forecast.csv-path in application.properties.");
        }

        String sheetName = null;
        if (storeId != null) {
            Store store = storeRepository.findById(storeId)
                    .orElseThrow(() -> new IllegalArgumentException("Store not found with ID: " + storeId));
            sheetName = store.getStoreName();
        }

        List<DayForecastDto> forecastDays = csvReaderService.readDataFromCsvForMonth(
                forecastCsvPath,
                targetMonth,
                sheetName
        );
        if (forecastDays.isEmpty()) {
            String source = sheetName == null ? "configured file" : "sheet " + sheetName;
            throw new IllegalArgumentException("No forecast rows found for " + targetMonth + " in " + source + ".");
        }

        LocalDate monthStart = targetMonth.atDay(1);
        LocalDate monthEnd = targetMonth.atEndOfMonth();
        List<Employee> targetEmployees = storeId == null
                ? employeeRepository.findAll()
                : employeeRepository.findByStoreId(storeId);

        if (targetEmployees.isEmpty()) {
            if (storeId == null) {
                throw new NoEmployeesException("No employees found in the database!");
            }
            throw new NoEmployeesException("No employees found for your store.");
        }

        List<Shift> existingShifts = storeId == null
                ? shiftRepository.findByShiftDateBetween(monthStart, monthEnd)
                : shiftRepository.findByEmployeeStoreIdAndShiftDateBetween(storeId, monthStart, monthEnd);

        if (!existingShifts.isEmpty()) {
            shiftRepository.deleteAll(existingShifts);
        }

        int generatedShifts = generateSchedule(forecastDays, targetEmployees);

        return new GenerateScheduleResponseDto(
                targetMonth.getYear(),
                targetMonth.getMonthValue(),
                forecastDays.size(),
                generatedShifts,
                storeId == null
                        ? "Shifts generated successfully."
                        : "Shifts generated successfully for your store."
        );
    }

    @Transactional
    public int generateSchedule(List<DayForecastDto> forecastDays) {
        return generateSchedule(forecastDays, employeeRepository.findAll());
    }

    @Transactional
    public int generateSchedule(List<DayForecastDto> forecastDays, List<Employee> allEmployees) {
        log.info("Initializing Heuristic CSP Solver...");
        if (allEmployees.isEmpty()) {
            throw new NoEmployeesException("No employees found in the database!");
        }
        List<EmployeeTracker> trackers = allEmployees.stream()
                .map(EmployeeTracker::new)
                .collect(Collectors.toList());

        List<Shift> shiftsToSave = new ArrayList<>();

        for (int day = 0; day < forecastDays.size(); day++) {

            DayForecastDto dayForecast = forecastDays.get(day);

            if (dayForecast.getDate().getDayOfWeek() == DayOfWeek.MONDAY) {
                trackers.forEach(EmployeeTracker::resetWeeklyHours);
            }

            int targetMorning = 1;
            int targetEvening = 1;

            DayOfWeek dow = dayForecast.getDate().getDayOfWeek();
            boolean isWeekend = (dow == DayOfWeek.SATURDAY) || (dow == DayOfWeek.SUNDAY);

            boolean isReceptionDay = dayForecast.getReceptionDay();
            boolean isDayBeforeReception = false;

            if (day + 1 < forecastDays.size()) {
                isDayBeforeReception = forecastDays.get(day + 1).getReceptionDay();
            }

            if (isWeekend || isReceptionDay || isDayBeforeReception) {
                targetMorning = 2;
                targetEvening = 2;
                log.info("HR Rule activated - we need at least 2 people on day {}.", dayForecast.getDate());
            }

            int projectedSale = dayForecast.getSalesForecast();

            if (projectedSale > BIG_SALES_THRESHOLD) {
                targetEvening++; // ZI AGLOMERATA - ADAUGAM INCA UN OM
                log.info("Big sales estimated ({}) for {}. Upped to {} people on the evening shift.",
                        projectedSale, dayForecast.getDate(), targetEvening);
            }
            if (projectedSale > MASSIVE_SALES_THRESHOLD) {
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
            /*
            "Inițial, am încercat să optimizez algoritmul Greedy pentru a grupa zilele libere ale angajaților (pentru work-life balance).
             Totuși, testând sistemul (QA), am observat că, pe echipe mici de 5 persoane, această constrângere "soft" intră în conflict cu constrângerile "hard" (limita legală de 5 zile lucrate consecutiv pentru ceilalți angajați).
              Astfel, am luat decizia arhitecturală de a prioritiza Acoperirea Magazinului (Coverage) și distribuția uniformă a orelor,
              lăsând gruparea zilelor libere pentru o iterație viitoare care ar putea folosi Meta-Heuristici mai avansate."
             */
           // availableTrackers.sort(Comparator.comparing(EmployeeTracker::needsSecondDayOff) // first we try to have employees with 2 straight days off
             //       .thenComparingInt(EmployeeTracker::getWorkedHoursCurrentMonth)); // then we sort them by the hour

            availableTrackers.sort(Comparator.comparingInt(EmployeeTracker::getWorkedHoursCurrentMonth));

            List<EmployeeTracker> assignedToday = new ArrayList<>();

            for (int man = 0; man < totalMenRequired; man++) {

                boolean needsMorning = currentMorningCount < targetMorning;

                boolean requiresFullTime = (needsMorning && currentMorningCount == 0) ||
                        (!needsMorning && currentEveningCount == 0);

                ShiftAssignment matchedAssignment = findBestCandidateForSlot(availableTrackers, needsMorning, requiresFullTime);

                if (matchedAssignment == null) {
                    log.warn("Did not find an employee on slot {} from {} necessary on day {}", man + 1, totalMenRequired, dayForecast.getDate());
                    break;
                }

                EmployeeTracker chosenTracker = matchedAssignment.tracker;

                if (matchedAssignment.proposal.isMorning) {
                    currentMorningCount++;
                }
                else {
                    currentEveningCount++;
                }

                assignEmployeesToShift(shiftsToSave, assignedToday, matchedAssignment.proposal.duration, chosenTracker, dayForecast, matchedAssignment.proposal.type);
            }

            for (EmployeeTracker tracker : trackers) {
                if (!assignedToday.contains(tracker)) {
                    tracker.registerDayOff();
                }
            }
        }

        shiftRepository.saveAll(shiftsToSave);

        log.info("✅ Successfully generated and saved {} shifts in the database!", shiftsToSave.size());
        return shiftsToSave.size();
    }

    private YearMonth resolveTargetMonth(Integer year, Integer month) {
        if (year == null && month == null) {
            return YearMonth.now().plusMonths(1);
        }

        if (year == null || month == null) {
            throw new IllegalArgumentException("Both year and month must be provided together.");
        }

        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12.");
        }

        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Year must be between 2000 and 2100.");
        }

        return YearMonth.of(year, month);
    }

    private ShiftAssignment findBestCandidateForSlot(List<EmployeeTracker> availableTrackers, boolean needsMorning, boolean requiresFullTime) {

        // We search for someone who PREFERS this shift
        for (int tracker = 0; tracker < availableTrackers.size(); tracker++) {
            EmployeeTracker candidate = availableTrackers.get(tracker);

            String contract = candidate.getEmployee().getContractType();
            if (requiresFullTime && (contract == null || !contract.equals("FULL_TIME_8H"))) {
                continue;
            }

            ShiftProposal proposal = generateShiftProposal(candidate, needsMorning);

            if (canWorkMoreHoursThisWeek(candidate, proposal.duration)) {
                String pref = candidate.getEmployee().getShiftPreference();

                boolean isMatch = (pref == null || pref.isBlank() || pref.equalsIgnoreCase("ANY"));
                if (needsMorning && "MORNING".equalsIgnoreCase(pref)) isMatch = true;
                if (!needsMorning && "EVENING".equalsIgnoreCase(pref)) isMatch = true;

                if (isMatch) {
                    availableTrackers.remove(tracker);
                    return new ShiftAssignment(candidate, proposal);
                }
            }
        }

        // Step 2: Fallback. No one wants the shift so we take the first person available legally.
        for (int tracker = 0; tracker < availableTrackers.size(); tracker++) {
            EmployeeTracker candidate = availableTrackers.get(tracker);

            String contract = candidate.getEmployee().getContractType();
            if (requiresFullTime && (contract == null || !contract.equals("FULL_TIME_8H"))) {
                continue;
            }

            ShiftProposal proposal = generateShiftProposal(candidate, needsMorning);

            if (canWorkMoreHoursThisWeek(candidate, proposal.duration)) {
                availableTrackers.remove(tracker);
                return new ShiftAssignment(candidate, proposal);
            }
        }

        return null; // No men available
    }

    private ShiftProposal generateShiftProposal (EmployeeTracker candidate, boolean needsMorning) {
        String contract = candidate.getEmployee().getContractType();

        if ("FULL_TIME_8H".equals(contract)) {
            return needsMorning ?
                    new ShiftProposal(8, "SHIFT_1_10_18", true) :
                    new ShiftProposal(8, "SHIFT_2_14_22", false);
        } else if ("PART_TIME_4H".equals(contract)) {
            return  needsMorning ?
                    new ShiftProposal(4, "PART_TIME_10_14", true) :
                    new ShiftProposal(4, "PART_TIME_16_20", false);
        } else if ("PART_TIME_6H".equals(contract)) {
            return needsMorning ?
                    new ShiftProposal(6, "PART_TIME_10_16", true) :
                    new ShiftProposal(6, "PART_TIME_16_22", false);
        }

        return  new ShiftProposal (8, "UNKNOWN", needsMorning); // SPECIAL CASE WHEN THE EMPLOYEE HAS CORRUPTED DATA IN THE DB
    }

    private boolean canWorkMoreHoursThisWeek(EmployeeTracker tracker, int incomingHoursOnShift) {
        int futureHours = tracker.getWorkedHoursCurrentWeek() + incomingHoursOnShift;
        String contract = tracker.getEmployee().getContractType();
        if (contract != null) {
            if (contract.equals("FULL_TIME_8H")) {
                return futureHours <= 40;
            }
            if (contract.equals("PART_TIME_6H")) {
                return futureHours <= 30;
            }
            if (contract.equals("PART_TIME_4H")) {
                return futureHours <= 20;
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

    private static class ShiftProposal {
        int duration;
        String type;
        boolean isMorning;

        public ShiftProposal (int duration, String type, boolean isMorning) {
            this.duration = duration;
            this.type = type;
            this.isMorning = isMorning;
        }
    }

    private static class ShiftAssignment {
        EmployeeTracker tracker;
        ShiftProposal proposal;

        public ShiftAssignment (EmployeeTracker tracker, ShiftProposal proposal) {
            this.tracker = tracker;
            this.proposal = proposal;
        }
    }

}
