package hu.riskguard.epr.api.dto;

import java.util.List;

/**
 * Response DTO for EPR config validation — validation result and error list.
 */
public record EprConfigValidateResponse(boolean valid, List<String> errors) {

    public static EprConfigValidateResponse ok() {
        return new EprConfigValidateResponse(true, List.of());
    }

    public static EprConfigValidateResponse failed(List<String> errors) {
        return new EprConfigValidateResponse(false, errors);
    }

    public static EprConfigValidateResponse from(boolean valid, List<String> errors) {
        return new EprConfigValidateResponse(valid, errors);
    }
}
