package com.licenta.licentabackend.service;

import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Shift;
import com.licenta.licentabackend.dto.DayForecastDto;
import com.licenta.licentabackend.repository.AbsenceRequestRepository;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.LeaveRequestRepository;
import com.licenta.licentabackend.repository.NotificationRepository;
import com.licenta.licentabackend.repository.ReplacementOfferRepository;
import com.licenta.licentabackend.repository.ShiftRepository;
import com.licenta.licentabackend.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private CsvReaderService csvReaderService;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private AbsenceRequestRepository absenceRequestRepository;

    @Mock
    private ReplacementOfferRepository replacementOfferRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private SchedulingService service;

    @BeforeEach
    void setUp() {
        service = new SchedulingService(
                employeeRepository,
                shiftRepository,
                storeRepository,
                csvReaderService,
                leaveRequestRepository,
                absenceRequestRepository,
                replacementOfferRepository,
                notificationRepository,
                ""
        );
    }

    @Test
    void generateScheduleForMonthFailsWhenCsvPathMissing() {
        assertThrows(IllegalArgumentException.class, () -> service.generateScheduleForMonth(2026, 5));
    }

    // -----------------------------------------------------------------------
    // Consecutive-day limit tests (hard constraint: max 5 consecutive days)
    // -----------------------------------------------------------------------

    @Test
    void schedulerRespectsConsecutiveDayLimit() {
        // With only 1 full-time employee, the scheduler cannot assign more than
        // 5 consecutive days. On the 6th day it should not find a candidate.
        Employee emp = makeEmployee(1L, "FULL_TIME_8H", "ANY");
        List<Employee> employees = List.of(emp);

        // 7 weekdays starting Monday 2026-07-06
        List<DayForecastDto> forecast = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 7, 6); // Monday
        for (int i = 0; i < 7; i++) {
            forecast.add(new DayForecastDto(start.plusDays(i), 500, false));
        }

        when(shiftRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int shiftsGenerated = service.generateSchedule(forecast, employees, Map.of());

        // Employee works days 1-5, must rest day 6, then can work day 7 = 6 shifts max
        assertTrue(shiftsGenerated <= 6,
                "Employee should not work more than 5 consecutive days; expected ≤6 shifts across 7 days, got " + shiftsGenerated);
    }

    // -----------------------------------------------------------------------
    // Weekly hour cap tests (hard constraint)
    // -----------------------------------------------------------------------

    @Test
    void schedulerRespectsWeeklyHourCapForFullTime() {
        // 5 full-time 8H shifts = 40 hours. A 6th day in the same week should
        // not be assigned because 48 > 40 for full-time employees.
        Employee emp = makeEmployee(1L, "FULL_TIME_8H", "ANY");
        List<Employee> employees = List.of(emp);

        // 6 weekdays Mon-Sat in the same ISO week
        List<DayForecastDto> forecast = new ArrayList<>();
        LocalDate monday = LocalDate.of(2026, 7, 6);
        for (int i = 0; i < 6; i++) {
            forecast.add(new DayForecastDto(monday.plusDays(i), 500, false));
        }

        when(shiftRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int shiftsGenerated = service.generateSchedule(forecast, employees, Map.of());

        // 40h / 8h per shift = 5 max shifts in one week
        assertTrue(shiftsGenerated <= 5,
                "Full-time employee should not exceed 40 weekly hours; expected ≤5 shifts, got " + shiftsGenerated);
    }

    @Test
    void schedulerRespectsWeeklyHourCapForPartTime4H() {
        Employee emp = makeEmployee(1L, "PART_TIME_4H", "ANY");
        List<Employee> employees = List.of(emp);

        // 6 weekdays Mon-Sat
        List<DayForecastDto> forecast = new ArrayList<>();
        LocalDate monday = LocalDate.of(2026, 7, 6);
        for (int i = 0; i < 6; i++) {
            forecast.add(new DayForecastDto(monday.plusDays(i), 500, false));
        }

        when(shiftRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int shiftsGenerated = service.generateSchedule(forecast, employees, Map.of());

        // 20h / 4h per shift = 5 max shifts in one week
        assertTrue(shiftsGenerated <= 5,
                "Part-time 4H employee should not exceed 20 weekly hours; expected ≤5 shifts, got " + shiftsGenerated);
    }

    // -----------------------------------------------------------------------
    // Approved leave exclusion (hard constraint)
    // -----------------------------------------------------------------------

    @Test
    void schedulerExcludesEmployeesOnApprovedLeave() {
        Employee empA = makeEmployee(1L, "FULL_TIME_8H", "MORNING");
        Employee empB = makeEmployee(2L, "FULL_TIME_8H", "EVENING");
        List<Employee> employees = List.of(empA, empB);

        LocalDate day = LocalDate.of(2026, 7, 8); // Wednesday
        List<DayForecastDto> forecast = List.of(
                new DayForecastDto(day, 500, false)
        );

        // Employee A is on approved leave on that day
        Map<LocalDate, Set<Long>> leaveMap = Map.of(day, Set.of(1L));

        when(shiftRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int shiftsGenerated = service.generateSchedule(forecast, employees, leaveMap);

        // Only empB should be assigned (employee A is on leave)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Shift>> captor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepository).saveAll(captor.capture());

        List<Shift> savedShifts = captor.getValue();
        boolean empAAssigned = savedShifts.stream()
                .anyMatch(s -> s.getEmployee().getId().equals(1L));

        assertFalse(empAAssigned,
                "Employee on approved leave should NOT be assigned any shifts.");
    }

    // -----------------------------------------------------------------------
    // Sales threshold tests (coverage scaling)
    // -----------------------------------------------------------------------

    @Test
    void bigSalesThresholdIncreasesEveningStaffing() {
        // With sales > 2000, evening staffing should increase by 1
        Employee empA = makeEmployee(1L, "FULL_TIME_8H", "MORNING");
        Employee empB = makeEmployee(2L, "FULL_TIME_8H", "EVENING");
        Employee empC = makeEmployee(3L, "FULL_TIME_8H", "EVENING");
        List<Employee> employees = List.of(empA, empB, empC);

        // A Wednesday (not weekend) with big sales
        LocalDate day = LocalDate.of(2026, 7, 8);
        List<DayForecastDto> forecast = List.of(
                new DayForecastDto(day, 2500, false) // > 2000 threshold
        );

        when(shiftRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int shiftsGenerated = service.generateSchedule(forecast, employees, Map.of());

        // Normal: 1 morning + 1 evening = 2. With big sales: 1 morning + 2 evening = 3
        assertTrue(shiftsGenerated >= 3,
                "Big sales threshold should produce at least 3 shifts (1M+2E); got " + shiftsGenerated);
    }

    @Test
    void massiveSalesThresholdIncreasesAllStaffing() {
        // With sales > 5000, both morning and evening increase
        Employee empA = makeEmployee(1L, "FULL_TIME_8H", "MORNING");
        Employee empB = makeEmployee(2L, "FULL_TIME_8H", "MORNING");
        Employee empC = makeEmployee(3L, "FULL_TIME_8H", "EVENING");
        Employee empD = makeEmployee(4L, "FULL_TIME_8H", "EVENING");
        List<Employee> employees = List.of(empA, empB, empC, empD);

        LocalDate day = LocalDate.of(2026, 7, 8); // Wednesday
        List<DayForecastDto> forecast = List.of(
                new DayForecastDto(day, 6000, false) // > 5000 threshold
        );

        when(shiftRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int shiftsGenerated = service.generateSchedule(forecast, employees, Map.of());

        // Massive: 2 morning + 3 evening = 5 (but > BIG also triggers +1 evening)
        // Actually: base 1M+1E, > 2000 → +1E = 1M+2E, > 5000 → +1M = 2M+2E = 4
        assertTrue(shiftsGenerated >= 4,
                "Massive sales threshold should produce at least 4 shifts (2M+2E); got " + shiftsGenerated);
    }

    // -----------------------------------------------------------------------
    // Mixed contract types
    // -----------------------------------------------------------------------

    @Test
    void mixedContractTypesProduceCorrectShiftTypes() {
        Employee fullTime = makeEmployee(1L, "FULL_TIME_8H", "MORNING");
        Employee partTime4 = makeEmployee(2L, "PART_TIME_4H", "EVENING");
        List<Employee> employees = List.of(fullTime, partTime4);

        LocalDate day = LocalDate.of(2026, 7, 8);
        List<DayForecastDto> forecast = List.of(
                new DayForecastDto(day, 500, false)
        );

        when(shiftRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.generateSchedule(forecast, employees, Map.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Shift>> captor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepository).saveAll(captor.capture());

        List<Shift> savedShifts = captor.getValue();

        // Full-time employee should get a SHIFT_1 or SHIFT_2 type
        Shift ftShift = savedShifts.stream()
                .filter(s -> s.getEmployee().getId().equals(1L))
                .findFirst()
                .orElse(null);

        assertNotNull(ftShift, "Full-time employee should be assigned a shift.");
        assertTrue(ftShift.getShiftType().startsWith("SHIFT_"),
                "Full-time shift type should start with 'SHIFT_', got: " + ftShift.getShiftType());

        // Part-time employee gets a PART_TIME shift type (if assigned)
        Shift ptShift = savedShifts.stream()
                .filter(s -> s.getEmployee().getId().equals(2L))
                .findFirst()
                .orElse(null);

        if (ptShift != null) {
            assertTrue(ptShift.getShiftType().startsWith("PART_TIME_"),
                    "Part-time shift type should start with 'PART_TIME_', got: " + ptShift.getShiftType());
        }
    }

    // -----------------------------------------------------------------------
    // Shift preference matching
    // -----------------------------------------------------------------------

    @Test
    void schedulerPrefersEmployeesWithMatchingShiftPreference() {
        Employee morningPref = makeEmployee(1L, "FULL_TIME_8H", "MORNING");
        Employee eveningPref = makeEmployee(2L, "FULL_TIME_8H", "EVENING");
        List<Employee> employees = List.of(morningPref, eveningPref);

        LocalDate day = LocalDate.of(2026, 7, 8); // Wednesday
        List<DayForecastDto> forecast = List.of(
                new DayForecastDto(day, 500, false)
        );

        when(shiftRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.generateSchedule(forecast, employees, Map.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Shift>> captor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepository).saveAll(captor.capture());

        List<Shift> savedShifts = captor.getValue();

        // Morning-preferring employee should get the morning shift (SHIFT_1_10_18)
        Shift morningShift = savedShifts.stream()
                .filter(s -> "SHIFT_1_10_18".equals(s.getShiftType()))
                .findFirst()
                .orElse(null);

        if (morningShift != null) {
            assertEquals(1L, morningShift.getEmployee().getId(),
                    "Morning-preferring employee should be assigned the morning shift.");
        }

        // Evening-preferring employee should get the evening shift (SHIFT_2_14_22)
        Shift eveningShift = savedShifts.stream()
                .filter(s -> "SHIFT_2_14_22".equals(s.getShiftType()))
                .findFirst()
                .orElse(null);

        if (eveningShift != null) {
            assertEquals(2L, eveningShift.getEmployee().getId(),
                    "Evening-preferring employee should be assigned the evening shift.");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Employee makeEmployee(Long id, String contractType, String shiftPreference) {
        Employee emp = new Employee();
        emp.setId(id);
        emp.setFullName("Employee " + id);
        emp.setContractType(contractType);
        emp.setShiftPreference(shiftPreference);
        emp.setRemainingLeaveDays(21);
        return emp;
    }
}
