package hu.riskguard.epr.registry.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Remaps bean-validation failures on {@link RegistryController} to RFC 7807
 * problem details with stable, machine-readable {@code type} URNs.
 *
 * <p>Story 9.5 AC #8 surfaces the "empty components list" error inline in the
 * UI with a specific i18n key. Spring's default validation handler emits a
 * generic {@code about:blank} type that the frontend cannot distinguish from
 * other 400s — hence this targeted mapping.</p>
 */
@ControllerAdvice(assignableTypes = RegistryController.class)
public class RegistryValidationExceptionHandler {

    static final URI COMPONENTS_REQUIRED_TYPE =
            URI.create("urn:riskguard:error:registry-components-required");

    static final URI VALIDATION_FAILED_TYPE =
            URI.create("urn:riskguard:error:registry-validation-failed");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        FieldError componentsError = ex.getBindingResult().getFieldErrors().stream()
                .filter(RegistryValidationExceptionHandler::isComponentsMinSize)
                .findFirst()
                .orElse(null);

        if (componentsError != null) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            problem.setType(COMPONENTS_REQUIRED_TYPE);
            problem.setTitle("Components required");
            problem.setDetail("At least one packaging component is required.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
        }

        // Collect per-field error messages so the frontend can show specifics.
        Map<String, List<String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())
                ));

        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(VALIDATION_FAILED_TYPE);
        problem.setTitle("Validation failed");
        problem.setDetail(detail);
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    private static boolean isComponentsMinSize(FieldError err) {
        if (!"components".equals(err.getField())) return false;
        String[] codes = err.getCodes();
        if (codes == null) return false;
        for (String code : codes) {
            if (code != null && code.startsWith("Size")) return true;
        }
        return false;
    }
}
