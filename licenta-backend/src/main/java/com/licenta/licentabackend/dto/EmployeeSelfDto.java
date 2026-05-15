package com.licenta.licentabackend.dto;

public record EmployeeSelfDto(
        Long id,
        String fullName,
        Integer remainingLeaveDays,
        Integer holidayRecoveryHours,
        Long storeId,
        String storeName
) {}
