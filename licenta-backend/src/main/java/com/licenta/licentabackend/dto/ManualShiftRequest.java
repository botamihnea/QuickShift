package com.licenta.licentabackend.dto;

import java.time.LocalDate;

public record ManualShiftRequest(
        LocalDate date,
        String shiftType,
        Long employeeId,
        Long storeId
) {}
