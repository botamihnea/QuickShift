package com.licenta.licentabackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateManagerRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Must be a valid email format.")
        String email,
        @NotNull(message = "Store ID is required.")
        @Positive(message = "Store ID must be a positive value.")
        Long storeId
) {}
