package hu.riskguard.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;

/**
 * Converts {@link TierUpgradeRequiredException} into an RFC 7807 response.
 * Scoped to this single exception — a broader GlobalExceptionHandler can be extracted later.
 */
@ControllerAdvice
public class TierGateExceptionHandler {

    @ExceptionHandler(TierUpgradeRequiredException.class)
    public ResponseEntity<ProblemDetail> handleTierUpgradeRequired(TierUpgradeRequiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("urn:riskguard:error:tier-upgrade-required"));
        problem.setTitle("Tier upgrade required");
        problem.setDetail(ex.getMessage());
        problem.setProperty("requiredTier", ex.getRequiredTier() != null ? ex.getRequiredTier().name() : null);
        problem.setProperty("currentTier", ex.getCurrentTier() != null ? ex.getCurrentTier().name() : null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }
}
