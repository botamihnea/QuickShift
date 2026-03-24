package com.licenta.licentabackend.dto;

public record GenerateScheduleResponseDto(
        Integer targetYear,
        Integer targetMonth,
        Integer forecastDaysUsed,
        Integer generatedShifts,
        String message
) {
}
