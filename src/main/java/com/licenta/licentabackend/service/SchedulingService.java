package com.licenta.licentabackend.service;

import com.licenta.licentabackend.domain.Employee;
import com.licenta.licentabackend.domain.Shift;
import com.licenta.licentabackend.dto.DayForecastDto;
import com.licenta.licentabackend.exceptions.NoEmployeesException;
import com.licenta.licentabackend.repository.EmployeeRepository;
import com.licenta.licentabackend.repository.ShiftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class SchedulingService {
    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);

    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;

    public SchedulingService(EmployeeRepository employeeRepository, ShiftRepository shiftRepository) {
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
    }

    public void generateSchedule(List<DayForecastDto> forecastDays) {
        List<Employee> allEmployees = employeeRepository.findAll();

        if (allEmployees.isEmpty()) {
            throw new NoEmployeesException("No employees found in the database!");
        }

        List<Shift> shiftsToSave = new ArrayList<>();

        for (DayForecastDto dayForecast : forecastDays) {
            int forecast = dayForecast.getSalesForecast();
            boolean isReceptionDay = dayForecast.getReceptionDay();
            int morningCount = 0;
            int eveningCount = 0;
            int partTimeCount = 0;

            if (forecast < 1000 && !isReceptionDay) {
                morningCount = 1;
                eveningCount = 1;
            } else if (forecast < 1000) {
                morningCount = 2;
                eveningCount = 1;
            } else if (forecast <= 2000 && !isReceptionDay) {
                morningCount = 2;
                eveningCount = 2;
            } else {
                morningCount = 2;
                eveningCount = 2;
                partTimeCount = 1;
            }

            List<Employee> availableToday = new ArrayList<>(allEmployees);

            assignEmployeesToShift(dayForecast.getDate(), "SHIFT_1_10_18", morningCount, availableToday, shiftsToSave);
            assignEmployeesToShift(dayForecast.getDate(), "SHIFT_2_14_22", eveningCount, availableToday, shiftsToSave);
            assignEmployeesToShift(dayForecast.getDate(), "PART_TIME_16_20", partTimeCount, availableToday, shiftsToSave);
        }

        shiftRepository.saveAll(shiftsToSave);

        log.info("✅ Successfully generated and saved {} shifts in the database!", shiftsToSave.size());
    }

    private void assignEmployeesToShift(LocalDate shiftDate, String shiftType, int needed_people, List<Employee> availableToday, List<Shift> shiftsToSave) {
        for (int i = 0; i < needed_people; i++) {
            if (!availableToday.isEmpty()) {
                Employee assignedEmployee = availableToday.removeFirst();

                Shift shift = new Shift();
                shift.setShiftDate(shiftDate);
                shift.setShiftType(shiftType);
                shift.setEmployee(assignedEmployee);

                shiftsToSave.add(shift);
            } else {
                log.warn("Not enough employees available to cover {} on {}", shiftType, shiftDate);
            }
        }
   }
}
