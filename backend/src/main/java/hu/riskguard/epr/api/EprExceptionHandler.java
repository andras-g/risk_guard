package hu.riskguard.epr.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;

/**
 * Maps FK RESTRICT violations on material-template deletion (Epic 10 Story 10.1 AC #4) to
 * RFC 7807 Problem Details. The UI layer consumes the stable {@code type} URN plus the
 * i18n key embedded in {@code detail} to render a user-safe "template still in use" toast.
 *
 * <p>Trigger: deleting a row from {@code epr_material_templates} while a
 * {@code product_packaging_components.material_template_id} still references it. Postgres
 * raises an FK-violation that Spring translates to {@link DataIntegrityViolationException}.
 */
@ControllerAdvice(assignableTypes = EprController.class)
public class EprExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EprExceptionHandler.class);

    static final URI TEMPLATE_STILL_REFERENCED_TYPE =
            URI.create("https://riskguard.hu/errors/epr/template-still-referenced");

    static final String TEMPLATE_STILL_REFERENCED_I18N_KEY =
            "epr.registry.template.stillReferenced";

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        // R3-P7: log the full exception so operators can correlate 409s to PG constraint names.
        // The response body deliberately omits these details to avoid leaking schema internals.
        log.warn("EPR data integrity violation", ex);

        String rootMessage = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage() : ex.getMessage();

        if (rootMessage != null
                && rootMessage.contains("product_packaging_components")
                && rootMessage.contains("material_template_id")) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
            problem.setType(TEMPLATE_STILL_REFERENCED_TYPE);
            problem.setTitle("Template still referenced");
            problem.setDetail(TEMPLATE_STILL_REFERENCED_I18N_KEY);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }

        // Non-template FK violation — return generic 409 without exposing internal schema details.
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Data integrity violation");
        problem.setDetail("A data integrity constraint was violated.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
