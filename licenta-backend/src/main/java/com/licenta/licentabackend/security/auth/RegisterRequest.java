package com.licenta.licentabackend.security.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required!")
        String fullName,
        @NotBlank(message = "Email is required!")
        @Email(message = "Must be a valid email format!")
        String email,
        @NotBlank(message = "Password is required!")
        @Size(min = 6, message = "Password must have at least 6 characters!")
        String password,
        @NotBlank(message = "Shift preference is required!")
        @Pattern(regexp = "MORNING|EVENING|ANY", message = "Shift preference must be MORNING, EVENING, or ANY.")
        String shiftPreference,
        @NotBlank(message = "Contract type is required!")
        @Pattern(
                regexp = "FULL_TIME_8H|PART_TIME_6H|PART_TIME_4H",
                message = "Contract type must be FULL_TIME_8H, PART_TIME_6H, or PART_TIME_4H."
        )
        String contractType,
        @NotNull(message = "Please select your assigned store!")
        @Positive(message = "Store ID must be a positive value.")
        Long storeId) {}
