package com.licenta.licentabackend.dto;

import java.time.LocalDate;

public record LeaveRequestResponse(
        Long id,
        String status,
        Integer requestedDays,
        Integer deductibleDays,
        LocalDate startDate,
        LocalDate endDate
) {}
