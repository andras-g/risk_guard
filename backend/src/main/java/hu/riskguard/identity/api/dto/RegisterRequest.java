package hu.riskguard.identity.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration request DTO for local (email/password) authentication.
 * Password must be at least 8 characters with 1 uppercase, 1 digit, and 1 special character.
 * The confirmPassword field must match password (server-side validation guard).
 */
public record RegisterRequest(
        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8)
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$",
                 message = "Password must contain at least 1 uppercase letter, 1 digit, and 1 special character")
        String password,

        @NotBlank
        String confirmPassword,

        @NotBlank
        String name
) {
    /**
     * Cross-field validation: confirmPassword must match password.
     * Prevents registration with mismatched passwords even if client-side check is bypassed.
     */
    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}
