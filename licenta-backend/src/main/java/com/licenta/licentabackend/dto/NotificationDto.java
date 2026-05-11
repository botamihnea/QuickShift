package com.licenta.licentabackend.dto;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        String message,
        LocalDateTime createdAt,
        boolean read,
        Long storeId,
        String storeName,
        Long relatedAbsenceRequestId  // null for regular notifications, non-null for absence alerts
) {}