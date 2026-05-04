package com.licenta.licentabackend.security.auth;

public record AuthenticatedUserResponse(
        String email,
        String role,
        Long storeId,
        String storeName
) {}