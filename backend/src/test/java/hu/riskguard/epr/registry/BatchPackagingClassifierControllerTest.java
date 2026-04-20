package hu.riskguard.epr.registry;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.epr.registry.api.BatchPackagingClassifierController;
import hu.riskguard.epr.registry.api.dto.BatchPackagingRequest;
import hu.riskguard.epr.registry.api.dto.BatchPackagingRequest.PairRequest;
import hu.riskguard.epr.registry.api.dto.BatchPackagingResponse;
import hu.riskguard.epr.registry.api.dto.BatchPackagingResult;
import hu.riskguard.epr.registry.api.dto.ClassifierUsageInfo;
import hu.riskguard.epr.registry.api.exception.BatchConcurrencyLimitExceededException;
import hu.riskguard.epr.registry.api.exception.ClassifierCapExceededException;
import hu.riskguard.epr.registry.domain.BatchPackagingClassifierService;
import hu.riskguard.epr.registry.domain.BatchPackagingConcurrencyGate;
import hu.riskguard.epr.registry.domain.ClassifierUsageService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchPackagingClassifierController} (Story 10.3 AC #26).
 *
 * <p>Mockito-only style matches {@link RegistryClassifyControllerTest} — the
 * controller is constructed directly so JWT, tier, role, gate, cap, and exception
 * flows can be exercised without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class BatchPackagingClassifierControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock private BatchPackagingClassifierService batchService;
    @Mock private ClassifierUsageService usageService;
    @Mock private BatchPackagingConcurrencyGate concurrencyGate;

    private BatchPackagingClassifierController controller;

    @BeforeEach
    void setUp() {
        controller = new BatchPackagingClassifierController(batchService, usageService, concurrencyGate);
    }

    // ─── (a) Happy path: 3-pair batch returns 200 BatchPackagingResponse ──────

    @Test
    void classify_threePairs_returnsBatchPackagingResponseWithUsageInfo() {
        when(usageService.getMonthlyCap()).thenReturn(1000);
        when(usageService.getCurrentMonthCallCount(TENANT_ID))
                .thenReturn(50)   // before
                .thenReturn(53);  // after
        when(concurrencyGate.tryAcquire(TENANT_ID)).thenReturn(true);

        List<BatchPackagingResult> mocked = List.of(
                BatchPackagingResult.unresolved("39233000", "PET"),
                BatchPackagingResult.unresolved("48191000", "Karton"),
                BatchPackagingResult.unresolved("76129020", "Alu"));
        when(batchService.classify(any(), eq(TENANT_ID))).thenReturn(mocked);

        BatchPackagingRequest request = new BatchPackagingRequest(List.of(
                new PairRequest("39233000", "PET"),
                new PairRequest("48191000", "Karton"),
                new PairRequest("76129020", "Alu")));

        BatchPackagingResponse response = controller.classify(request, buildJwt("SME_ADMIN"));

        assertThat(response.results()).hasSize(3);
        assertThat(response.usageInfo().callsUsedThisMonth()).isEqualTo(53);
        assertThat(response.usageInfo().callsRemaining()).isEqualTo(947);
        assertThat(response.usageInfo().monthlyCap()).isEqualTo(1000);
        verify(concurrencyGate).release(TENANT_ID);
    }

    // ─── (b) Bean-validation cases (validated outside Spring context) ─────────

    @Test
    void request_emptyPairs_failsBeanValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator v = factory.getValidator();
            BatchPackagingRequest req = new BatchPackagingRequest(List.of());
            Set<ConstraintViolation<BatchPackagingRequest>> violations = v.validate(req);
            assertThat(violations).anyMatch(c -> c.getPropertyPath().toString().equals("pairs"));
        }
    }

    @Test
    void request_oneHundredOnePairs_failsBeanValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator v = factory.getValidator();
            List<PairRequest> pairs = new java.util.ArrayList<>();
            for (int i = 0; i < 101; i++) pairs.add(new PairRequest("12345678", "x"));
            BatchPackagingRequest req = new BatchPackagingRequest(pairs);
            Set<ConstraintViolation<BatchPackagingRequest>> violations = v.validate(req);
            assertThat(violations).anyMatch(c -> c.getPropertyPath().toString().equals("pairs"));
        }
    }

    @Test
    void request_invalidVtszPattern_failsBeanValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator v = factory.getValidator();
            // AC #2 / #26(b): error detail must include the list index, not just "vtsz".
            BatchPackagingRequest req = new BatchPackagingRequest(List.of(
                    new PairRequest("12345678", "ok"),
                    new PairRequest("12345678", "ok"),
                    new PairRequest("12345678", "ok"),
                    new PairRequest("abc",      "PET")));
            Set<ConstraintViolation<BatchPackagingRequest>> violations = v.validate(req);
            assertThat(violations)
                    .as("violation path must be indexed (pairs[3].vtsz)")
                    .anyMatch(c -> {
                        String p = c.getPropertyPath().toString();
                        return p.contains("pairs[3]") && p.endsWith(".vtsz");
                    });
        }
    }

    @Test
    void request_blankDescription_failsBeanValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator v = factory.getValidator();
            // AC #2 / #26(b): indexed error detail for the offending pair.
            BatchPackagingRequest req = new BatchPackagingRequest(List.of(
                    new PairRequest("12345678", "ok"),
                    new PairRequest("12345678", "")));
            Set<ConstraintViolation<BatchPackagingRequest>> violations = v.validate(req);
            assertThat(violations)
                    .as("violation path must be indexed (pairs[1].description)")
                    .anyMatch(c -> {
                        String p = c.getPropertyPath().toString();
                        return p.contains("pairs[1]") && p.endsWith(".description");
                    });
        }
    }

    // ─── (c) Missing active_tenant_id JWT claim → 401 ─────────────────────────

    @Test
    void classify_missingTenantClaim_throws401() {
        Jwt jwtNoTenant = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("role", "SME_ADMIN")
                .build();

        assertThatThrownBy(() -> controller.classify(
                new BatchPackagingRequest(List.of(new PairRequest("12345678", "x"))), jwtNoTenant))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(batchService, concurrencyGate);
    }

    // ─── (d) Role gating: GUEST/SME_USER → 403; SME_ADMIN/ACCOUNTANT/PLATFORM_ADMIN → 200 ─

    @Test
    void classify_guestRole_throws403() {
        assertThatThrownBy(() -> controller.classify(
                new BatchPackagingRequest(List.of(new PairRequest("12345678", "x"))),
                buildJwt("GUEST")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(batchService, concurrencyGate);
    }

    @Test
    void classify_smeUserRole_throws403() {
        assertThatThrownBy(() -> controller.classify(
                new BatchPackagingRequest(List.of(new PairRequest("12345678", "x"))),
                buildJwt("SME_USER")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(batchService, concurrencyGate);
    }

    @Test
    void classify_accountantRole_passes() {
        when(usageService.getMonthlyCap()).thenReturn(1000);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0).thenReturn(1);
        when(concurrencyGate.tryAcquire(TENANT_ID)).thenReturn(true);
        when(batchService.classify(any(), eq(TENANT_ID))).thenReturn(List.of(
                BatchPackagingResult.unresolved("12345678", "x")));

        BatchPackagingResponse response = controller.classify(
                new BatchPackagingRequest(List.of(new PairRequest("12345678", "x"))),
                buildJwt("ACCOUNTANT"));
        assertThat(response.results()).hasSize(1);
    }

    @Test
    void classify_smeAdminRole_passes() {
        when(usageService.getMonthlyCap()).thenReturn(1000);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0).thenReturn(1);
        when(concurrencyGate.tryAcquire(TENANT_ID)).thenReturn(true);
        when(batchService.classify(any(), eq(TENANT_ID))).thenReturn(List.of(
                BatchPackagingResult.unresolved("12345678", "x")));

        BatchPackagingResponse response = controller.classify(
                new BatchPackagingRequest(List.of(new PairRequest("12345678", "x"))),
                buildJwt("SME_ADMIN"));
        assertThat(response.results()).hasSize(1);
    }

    @Test
    void classify_platformAdminRole_passes() {
        when(usageService.getMonthlyCap()).thenReturn(1000);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0).thenReturn(1);
        when(concurrencyGate.tryAcquire(TENANT_ID)).thenReturn(true);
        when(batchService.classify(any(), eq(TENANT_ID))).thenReturn(List.of(
                BatchPackagingResult.unresolved("12345678", "x")));

        BatchPackagingResponse response = controller.classify(
                new BatchPackagingRequest(List.of(new PairRequest("12345678", "x"))),
                buildJwt("PLATFORM_ADMIN"));
        assertThat(response.results()).hasSize(1);
    }

    // ─── (e) @TierRequired(PRO_EPR) on the controller class ──────────────────

    @Test
    void controller_hasProEprTierAnnotation() {
        TierRequired annotation =
                BatchPackagingClassifierController.class.getAnnotation(TierRequired.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(Tier.PRO_EPR);
    }

    // ─── (f) Cap exceeded → 429 + Retry-After + ClassifierUsageInfo body ────

    @Test
    void classify_capPreCheckFails_throwsCapExceeded() {
        when(usageService.getMonthlyCap()).thenReturn(100);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(99);
        when(concurrencyGate.tryAcquire(TENANT_ID)).thenReturn(true);

        BatchPackagingRequest req = new BatchPackagingRequest(List.of(
                new PairRequest("12345678", "a"),
                new PairRequest("12345678", "b")));

        assertThatThrownBy(() -> controller.classify(req, buildJwt("SME_ADMIN")))
                .isInstanceOf(ClassifierCapExceededException.class)
                .satisfies(ex -> {
                    ClassifierCapExceededException c = (ClassifierCapExceededException) ex;
                    assertThat(c.usageInfo().callsRemaining()).isEqualTo(1);
                    assertThat(c.usageInfo().monthlyCap()).isEqualTo(100);
                    assertThat(c.requestedPairs()).isEqualTo(2);
                });
        verify(batchService, never()).classify(any(), any());
        verify(concurrencyGate).release(TENANT_ID);  // released even on rejection
    }

    @Test
    void handleCapExceeded_returns429WithRetryAfterAndUsageInfoBody() {
        ClassifierUsageInfo info = ClassifierUsageInfo.of(99, 100);
        ClassifierCapExceededException ex = new ClassifierCapExceededException(info, 5);

        ResponseEntity<ClassifierUsageInfo> response = controller.handleCapExceeded(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isEqualTo(info);
        String retryAfter = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        long retrySeconds = Long.parseLong(retryAfter);
        assertThat(retrySeconds).isGreaterThan(0L);
        // <= 32 days in seconds — Europe/Budapest month boundary
        assertThat(retrySeconds).isLessThanOrEqualTo(32L * 24 * 3600);
    }

    // ─── (g) Concurrent-batch gate at permit=0 → 429 ─────────────────────────

    @Test
    void classify_concurrencyGateExhausted_throwsConcurrencyLimitExceeded() {
        when(concurrencyGate.tryAcquire(TENANT_ID)).thenReturn(false);

        assertThatThrownBy(() -> controller.classify(
                new BatchPackagingRequest(List.of(new PairRequest("12345678", "x"))),
                buildJwt("SME_ADMIN")))
                .isInstanceOf(BatchConcurrencyLimitExceededException.class);

        verify(batchService, never()).classify(any(), any());
        verify(concurrencyGate, never()).release(TENANT_ID);  // never acquired → never released
    }

    @Test
    void handleConcurrencyLimit_returns429WithRetryAfter5() {
        BatchConcurrencyLimitExceededException ex = new BatchConcurrencyLimitExceededException(3);

        ResponseEntity<String> response = controller.handleConcurrencyLimit(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).contains("Concurrent batch limit (3) exceeded for tenant");
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static Jwt buildJwt(String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", TENANT_ID.toString())
                .claim("role", role)
                .build();
    }
}
