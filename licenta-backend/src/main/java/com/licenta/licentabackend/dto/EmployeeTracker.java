package com.licenta.licentabackend.dto;
import com.licenta.licentabackend.domain.Employee;
import lombok.Getter;

@Getter //genereaza automat getterele
public class EmployeeTracker {

    private final Employee employee;

    private int workedHoursCurrentMonth;
    private int workedHoursCurrentWeek;
    private int consecutiveWorkedDays;

    private boolean workedYesterday;
    private boolean had12HourShiftYesterday;
    private int consecutiveDaysOff;

    public EmployeeTracker (Employee employee) {
        this.employee = employee;
        this.workedHoursCurrentMonth = 0;
        this.workedHoursCurrentWeek = 0;
        this.consecutiveWorkedDays = 0;
        this.workedYesterday = false;
        this.had12HourShiftYesterday = false;
        this.consecutiveDaysOff  = 0;
    }

    public void assignShift(int hours) {
        this.workedHoursCurrentMonth += hours;
        this.workedHoursCurrentWeek += hours;
        this.consecutiveWorkedDays++;
        this.workedYesterday = true;
        this.had12HourShiftYesterday = (hours == 12);
        this.consecutiveDaysOff = 0;
    }

    public void registerDayOff() {
        this.consecutiveWorkedDays = 0;
        this.workedYesterday = false;
        this.had12HourShiftYesterday = false;
        this.consecutiveDaysOff++;
    }

    public void resetWeeklyHours() {
        this.workedHoursCurrentWeek = 0;
    }


    public Employee getEmployee() {
        return employee;
    }

    public int getWorkedHoursCurrentMonth() {
        return workedHoursCurrentMonth;
    }

    public int getWorkedHoursCurrentWeek() {
        return workedHoursCurrentWeek;
    }

    public int getConsecutiveWorkedDays() {
        return consecutiveWorkedDays;
    }

    public boolean isWorkedYesterday() {
        return workedYesterday;
    }

    public boolean isHad12HourShiftYesterday() {
        return had12HourShiftYesterday;
    }

    public int getConsecutiveDaysOff() {
        return consecutiveDaysOff;
    }

    public boolean needsSecondDayOff() {
        return this.consecutiveDaysOff == 1;
    }

}
