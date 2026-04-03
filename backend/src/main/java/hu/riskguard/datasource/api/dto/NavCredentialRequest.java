package hu.riskguard.datasource.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for saving NAV Online Számla technical user credentials.
 * All fields are required — the raw password is hashed (SHA-512) before storage.
 *
 * @param login       NAV technical user login name
 * @param password    raw password (will be SHA-512 hashed before storage)
 * @param signingKey  request signing key from NAV technical user profile
 * @param exchangeKey token exchange key from NAV technical user profile (16 chars / AES-128)
 * @param taxNumber   8-digit Hungarian tax number of the company owning the credentials
 */
public record NavCredentialRequest(
        @NotBlank String login,
        @NotBlank String password,
        @NotBlank String signingKey,
        @NotBlank String exchangeKey,
        @NotBlank String taxNumber
) {}
