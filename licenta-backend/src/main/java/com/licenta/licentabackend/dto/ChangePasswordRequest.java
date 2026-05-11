package com.licenta.licentabackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required.")
        String currentPassword,
        @NotBlank(message = "New password is required.")
        @Size(min = 6, message = "Password must have at least 6 characters.")
        String newPassword
) {}
