package com.licenta.licentabackend.security.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthenticationRequest(
        @NotBlank(message = "Email is required!")
        @Email(message = "Must be a valid email format!")
        String email,
        @NotBlank(message = "Password is required!")
        @Size(min = 6, message = "Password must have at least 6 characters!")
        String password) {}
