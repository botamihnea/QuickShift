package com.licenta.licentabackend.dto;

import java.time.LocalDate;

public record ShiftDto(
        Long id,
        LocalDate shiftDate,
        String shiftType,
        String status,
        EmployeeSummary employee
) {
    public record EmployeeSummary(Long id, String fullName) {}
}

