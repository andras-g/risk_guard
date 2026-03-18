package hu.riskguard.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateLanguageRequest(
        @NotBlank @Pattern(regexp = "^(hu|en)$") String language
) {}
