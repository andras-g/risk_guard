package hu.riskguard.epr.registry;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.epr.registry.api.RegistryClassifyController;
import hu.riskguard.epr.registry.api.dto.ClassifyRequest;
import hu.riskguard.epr.registry.api.dto.ClassifyResponse;
import hu.riskguard.epr.registry.classifier.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RegistryClassifyController}.
 * Verifies JWT validation, successful classification, and empty result pass-through.
 */
@ExtendWith(MockitoExtension.class)
class RegistryClassifyControllerTest {

    @Mock private KfCodeClassifierService classifierService;

    private RegistryClassifyController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new RegistryClassifyController(classifierService);
    }

    // ─── Test 1: valid request returns classification response ────────────────

    @Test
    void classify_validRequest_returnsClassifyResponse() {
        ClassificationResult geminiResult = new ClassificationResult(
                List.of(new KfSuggestion("11010101", "PET", 0.90, "primary", null, 1)),
                ClassificationStrategy.VERTEX_GEMINI,
                ClassificationConfidence.HIGH,
                "gemini-3.0-flash-preview",
                Instant.now(),
                120, 45
        );
        when(classifierService.classify("PET palack", "39239090")).thenReturn(geminiResult);

        ClassifyRequest request = new ClassifyRequest("PET palack", "39239090");
        ClassifyResponse response = controller.classify(request, buildJwt());

        assertThat(response.strategy()).isEqualTo("VERTEX_GEMINI");
        assertThat(response.confidence()).isEqualTo("HIGH");
        assertThat(response.suggestions()).hasSize(1);
        assertThat(response.suggestions().get(0).kfCode()).isEqualTo("11010101");
    }

    // ─── Test 2: empty result from classifier passes through ──────────────────

    @Test
    void classify_emptyResult_returnsEmptyResponse() {
        when(classifierService.classify(anyString(), any())).thenReturn(ClassificationResult.empty());

        ClassifyRequest request = new ClassifyRequest("Ismeretlen anyag", null);
        ClassifyResponse response = controller.classify(request, buildJwt());

        assertThat(response.suggestions()).isEmpty();
        assertThat(response.strategy()).isEqualTo("NONE");
        assertThat(response.confidence()).isEqualTo("LOW");
    }

    // ─── Test 3: missing active_tenant_id claim throws 401 ───────────────────

    @Test
    void classify_missingTenantClaim_throws401() {
        ClassifyRequest request = new ClassifyRequest("Termék A", "39239090");
        Jwt jwtWithoutTenant = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("role", "SME_ADMIN")
                .build();

        assertThatThrownBy(() -> controller.classify(request, jwtWithoutTenant))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(classifierService);
    }

    // ─── Test 4: controller class has @TierRequired(PRO_EPR) — 403 gate ──────

    @Test
    void controller_hasProEprTierAnnotation() {
        TierRequired annotation = RegistryClassifyController.class.getAnnotation(TierRequired.class);
        assertThat(annotation)
                .as("@TierRequired must be present so TierGateInterceptor returns 403 for non-PRO_EPR tenants")
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(Tier.PRO_EPR);
    }

    // ─── Test 5: bean validation rejects malformed vtsz (400) ───────────────

    @Test
    void classifyRequest_badVtszPattern_failsBeanValidation() {
        try (jakarta.validation.ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            ClassifyRequest badRequest = new ClassifyRequest("Termék A", "abc");
            Set<ConstraintViolation<ClassifyRequest>> violations = validator.validate(badRequest);

            assertThat(violations)
                    .as("vtsz must fail @Pattern regex — Spring MVC returns 400 for @Valid failures")
                    .anyMatch(v -> v.getPropertyPath().toString().equals("vtsz"));
        }
    }

    @Test
    void classifyRequest_nullVtsz_passesBeanValidation() {
        try (jakarta.validation.ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            ClassifyRequest okRequest = new ClassifyRequest("Termék A", null);
            Set<ConstraintViolation<ClassifyRequest>> violations = validator.validate(okRequest);

            assertThat(violations)
                    .as("null vtsz must be accepted (@Nullable + @Pattern)")
                    .isEmpty();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Jwt buildJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", TENANT_ID.toString())
                .claim("role", "SME_ADMIN")
                .build();
    }
}
