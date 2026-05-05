package com.licenta.licentabackend.dto;

public record EmployeeSummaryDto(
        Long id,
        String fullName,
        String contractType,
        String shiftPreference,
        Integer remainingLeaveDays,
        Integer holidayRecoveryHours
) {}