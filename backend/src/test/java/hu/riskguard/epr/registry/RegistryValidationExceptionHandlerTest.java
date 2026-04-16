package hu.riskguard.epr.registry;

import hu.riskguard.epr.registry.api.RegistryValidationExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RegistryValidationExceptionHandler}.
 *
 * <p>Story 9.5 AC #8: when a POST/PUT arrives with an empty components list,
 * the controller advice must remap the generic bean-validation failure to a
 * distinct RFC 7807 problem detail with type
 * {@code urn:riskguard:error:registry-components-required} so the frontend
 * can surface the exact i18n message.</p>
 */
class RegistryValidationExceptionHandlerTest {

    private final RegistryValidationExceptionHandler handler = new RegistryValidationExceptionHandler();

    @Test
    void emptyComponentsList_mapsToComponentsRequiredProblemDetail() throws Exception {
        MethodArgumentNotValidException ex = buildMinSizeComponentsException();

        ResponseEntity<ProblemDetail> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType())
                .isEqualTo(URI.create("urn:riskguard:error:registry-components-required"));
        assertThat(body.getTitle()).isEqualTo("Components required");
        assertThat(body.getStatus()).isEqualTo(400);
    }

    @Test
    void otherFieldValidation_returnsValidationFailedWithFieldDetails() throws Exception {
        MethodArgumentNotValidException ex = buildNameBlankException();

        ResponseEntity<ProblemDetail> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType())
                .isEqualTo(URI.create("urn:riskguard:error:registry-validation-failed"));
        assertThat(body.getTitle()).isEqualTo("Validation failed");
        assertThat(body.getDetail()).contains("name").contains("must not be blank");
        assertThat(body.getProperties()).containsKey("fieldErrors");
    }

    @Test
    void componentsFieldWithNonSizeCode_doesNotMatchComponentsRequiredUrn() throws Exception {
        // A FieldError on "components" that is NOT a Size violation should not
        // be remapped to the "components-required" URN.
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "productUpsertRequest");
        FieldError err = new FieldError(
                "productUpsertRequest", "components",
                null, false, new String[] { "NotNull", "NotNull.components" },
                null, "must not be null");
        br.addError(err);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyParameter(), br);

        ResponseEntity<ProblemDetail> response = handler.handleValidation(ex);

        assertThat(response.getBody().getType())
                .isEqualTo(URI.create("urn:riskguard:error:registry-validation-failed"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static MethodArgumentNotValidException buildMinSizeComponentsException() throws Exception {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "productUpsertRequest");
        FieldError err = new FieldError(
                "productUpsertRequest", "components",
                null, false,
                new String[] { "Size", "Size.productUpsertRequest.components", "Size.components" },
                new Object[] { 0, Integer.MAX_VALUE, 1 },
                "size must be between 1 and 2147483647");
        br.addError(err);
        return new MethodArgumentNotValidException(dummyParameter(), br);
    }

    private static MethodArgumentNotValidException buildNameBlankException() throws Exception {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "productUpsertRequest");
        FieldError err = new FieldError(
                "productUpsertRequest", "name",
                "", false,
                new String[] { "NotBlank", "NotBlank.name" },
                null, "must not be blank");
        br.addError(err);
        return new MethodArgumentNotValidException(dummyParameter(), br);
    }

    private static org.springframework.core.MethodParameter dummyParameter() throws NoSuchMethodException {
        Method m = RegistryValidationExceptionHandlerTest.class
                .getDeclaredMethod("dummyParameter");
        return new org.springframework.core.MethodParameter(m, -1);
    }
}
