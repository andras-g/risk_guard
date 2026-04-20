package hu.riskguard.epr.registry.bootstrap.domain;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Carries a machine-readable {@code code} and optional extra body properties
 * (e.g. {@code jobId} for the in-flight guard) so the controller's
 * {@code @ExceptionHandler} can render a structured JSON body
 * ({@code { "code": "...", "message": "..." }}) instead of Spring's generic
 * {@code { "status", "error", "message" }} payload.
 *
 * <p>Covers AC #12 (412 with code), AC #13 (409 ALREADY_RUNNING with jobId),
 * and AC #11 (403 tax-number mismatch).
 */
public class BootstrapPreconditionException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> extraProperties;

    public BootstrapPreconditionException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public BootstrapPreconditionException(HttpStatus status, String code, String message,
                                           Map<String, Object> extraProperties) {
        super(message);
        this.status = status;
        this.code = code;
        this.extraProperties = extraProperties;
    }

    public static BootstrapPreconditionException alreadyRunning(UUID existingJobId) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("jobId", existingJobId);
        return new BootstrapPreconditionException(
                HttpStatus.CONFLICT, "ALREADY_RUNNING",
                "Bootstrap job already in progress: " + existingJobId, extra);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> extraProperties() {
        return extraProperties;
    }
}
