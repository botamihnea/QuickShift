package com.licenta.licentabackend.dto;

public record CreateStoreRequest(
        String storeName,
        String address,
        Double busyDaySalesThreshold) {}
