package com.licenta.licentabackend.dto;

public record EligibleEmployeeDto(
        Long id,
        String fullName,
        String contractType,
        String shiftPreference
) {}
