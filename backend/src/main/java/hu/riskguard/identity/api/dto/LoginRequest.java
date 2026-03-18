package hu.riskguard.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login request DTO for local (email/password) authentication.
 */
public record LoginRequest(
        @NotBlank @Email
        String email,

        @NotBlank
        String password
) {}
