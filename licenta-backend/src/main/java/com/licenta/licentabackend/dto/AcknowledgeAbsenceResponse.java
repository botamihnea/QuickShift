package com.licenta.licentabackend.dto;

public record AcknowledgeAbsenceResponse(
        boolean replacementFound,
        String replacementEmployeeName  // null when replacementFound is false
) {}
