package com.licenta.licentabackend.security.auth;

public record AuthenticatedUserResponse(
        String email,
        String role,
        String fullName,
        Long storeId,
        String storeName
) {}