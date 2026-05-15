package com.licenta.licentabackend.dto;

import java.time.LocalDate;

public record LeaveRequestCreateRequest(
        LocalDate startDate,
        LocalDate endDate,
        String reason
) {}
