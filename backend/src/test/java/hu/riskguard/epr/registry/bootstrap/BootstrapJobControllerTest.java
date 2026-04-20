package hu.riskguard.epr.registry.bootstrap;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.epr.registry.bootstrap.api.BootstrapJobController;
import hu.riskguard.epr.registry.bootstrap.api.BootstrapJobStatusResponse;
import hu.riskguard.epr.registry.bootstrap.api.BootstrapTriggerRequest;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobRecord;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobStatus;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapPreconditionException;
import hu.riskguard.epr.registry.bootstrap.domain.InvoiceDrivenRegistryBootstrapService;
import hu.riskguard.epr.registry.bootstrap.internal.BootstrapJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BootstrapJobController} covering AC #33.
 */
@ExtendWith(MockitoExtension.class)
class BootstrapJobControllerTest {

    @Mock InvoiceDrivenRegistryBootstrapService bootstrapService;
    @Mock BootstrapJobRepository bootstrapJobRepository;

    private BootstrapJobController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID JOB_ID    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new BootstrapJobController(bootstrapService, bootstrapJobRepository);
    }

    // ── (a) POST 202 with default period ──────────────────────────────────────

    @Test
    void trigger_defaultPeriod_returns202() {
        when(bootstrapService.startJob(eq(TENANT_ID), eq(USER_ID), eq(null), eq(null)))
                .thenReturn(JOB_ID);

        ResponseEntity<?> response = controller.trigger(null, adminJwt());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).contains(JOB_ID.toString());
    }

    // ── (b) POST 202 with explicit period ─────────────────────────────────────

    @Test
    void trigger_explicitPeriod_returnsJobId() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to   = LocalDate.of(2026, 3, 31);
        when(bootstrapService.startJob(eq(TENANT_ID), eq(USER_ID), eq(from), eq(to)))
                .thenReturn(JOB_ID);

        ResponseEntity<?> response = controller.trigger(new BootstrapTriggerRequest(from, to), adminJwt());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    // ── (c) POST 412 on missing NAV credentials — structured body via @ExceptionHandler ─
    //
    // Production throws BootstrapPreconditionException (not ResponseStatusException); the
    // controller's @ExceptionHandler turns it into a 412 with a { code, message } body.
    // Asserting via the handler covers both the throw path AND the body-rendering path.

    @Test
    void trigger_navCredentialsMissing_rendersStructured412() {
        BootstrapPreconditionException ex = new BootstrapPreconditionException(
                HttpStatus.PRECONDITION_FAILED, "NAV_CREDENTIALS_MISSING", "NAV credentials missing");
        when(bootstrapService.startJob(any(), any(), any(), any())).thenThrow(ex);

        assertThatThrownBy(() -> controller.trigger(null, adminJwt()))
                .isInstanceOf(BootstrapPreconditionException.class);

        ResponseEntity<java.util.Map<String, Object>> response = controller.handlePrecondition(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(response.getBody()).containsEntry("code", "NAV_CREDENTIALS_MISSING");
    }

    // ── (d) POST 409 in-flight guard — body carries jobId ─────────────────────

    @Test
    void trigger_alreadyRunning_rendersStructured409WithJobId() {
        BootstrapPreconditionException ex = BootstrapPreconditionException.alreadyRunning(JOB_ID);
        when(bootstrapService.startJob(any(), any(), any(), any())).thenThrow(ex);

        assertThatThrownBy(() -> controller.trigger(null, adminJwt()))
                .isInstanceOf(BootstrapPreconditionException.class);

        ResponseEntity<java.util.Map<String, Object>> response = controller.handlePrecondition(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "ALREADY_RUNNING");
        assertThat(response.getBody()).containsEntry("jobId", JOB_ID);
    }

    // ── (e) POST 403 on tax-number mismatch ──────────────────────────────────

    @Test
    void trigger_taxNumberMismatch_rendersStructured403() {
        BootstrapPreconditionException ex = new BootstrapPreconditionException(
                HttpStatus.FORBIDDEN, "TAX_NUMBER_MISMATCH", "Tax number mismatch");
        when(bootstrapService.startJob(any(), any(), any(), any())).thenThrow(ex);

        assertThatThrownBy(() -> controller.trigger(null, adminJwt()))
                .isInstanceOf(BootstrapPreconditionException.class);

        ResponseEntity<java.util.Map<String, Object>> response = controller.handlePrecondition(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("code", "TAX_NUMBER_MISMATCH");
    }

    // ── (f) GET 404 on unknown id ─────────────────────────────────────────────

    @Test
    void getStatus_unknownId_returns404() {
        when(bootstrapJobRepository.findTenantForJob(JOB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getStatus(JOB_ID, adminJwt()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── (g) GET 200 on known id ───────────────────────────────────────────────

    @Test
    void getStatus_knownId_returns200() {
        when(bootstrapJobRepository.findTenantForJob(JOB_ID)).thenReturn(Optional.of(TENANT_ID));
        when(bootstrapJobRepository.findByIdAndTenant(JOB_ID, TENANT_ID))
                .thenReturn(Optional.of(buildJobRecord(BootstrapJobStatus.RUNNING)));

        BootstrapJobStatusResponse result = controller.getStatus(JOB_ID, adminJwt());

        assertThat(result.jobId()).isEqualTo(JOB_ID);
        assertThat(result.status()).isEqualTo(BootstrapJobStatus.RUNNING);
    }

    // ── AC #33 cross-tenant: GET on someone else's job → 403 ─────────────────

    @Test
    void getStatus_crossTenant_returns403() {
        UUID otherTenant = UUID.randomUUID();
        when(bootstrapJobRepository.findTenantForJob(JOB_ID)).thenReturn(Optional.of(otherTenant));

        assertThatThrownBy(() -> controller.getStatus(JOB_ID, adminJwt()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── AC #33 cross-tenant: DELETE on someone else's job → 403 ──────────────

    @Test
    void cancel_crossTenant_returns403() {
        UUID otherTenant = UUID.randomUUID();
        when(bootstrapJobRepository.cancelIfActive(JOB_ID, TENANT_ID)).thenReturn(0);
        when(bootstrapJobRepository.findTenantForJob(JOB_ID)).thenReturn(Optional.of(otherTenant));

        assertThatThrownBy(() -> controller.cancel(JOB_ID, adminJwt()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── (h) DELETE 204 on active job — atomic conditional UPDATE ──────────────

    @Test
    void cancel_activeJob_returns204() {
        when(bootstrapJobRepository.cancelIfActive(JOB_ID, TENANT_ID)).thenReturn(1);

        ResponseEntity<?> response = controller.cancel(JOB_ID, adminJwt());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(bootstrapJobRepository).cancelIfActive(JOB_ID, TENANT_ID);
        verify(bootstrapJobRepository, never()).transitionStatus(any(), any(), any());
    }

    // ── (i) DELETE 409 on terminal job — zero-row UPDATE then 409 body ────────

    @Test
    void cancel_terminalJob_returns409() {
        when(bootstrapJobRepository.cancelIfActive(JOB_ID, TENANT_ID)).thenReturn(0);
        when(bootstrapJobRepository.findTenantForJob(JOB_ID)).thenReturn(Optional.of(TENANT_ID));
        when(bootstrapJobRepository.findByIdAndTenant(JOB_ID, TENANT_ID))
                .thenReturn(Optional.of(buildJobRecord(BootstrapJobStatus.COMPLETED)));

        ResponseEntity<?> response = controller.cancel(JOB_ID, adminJwt());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("code", "ALREADY_TERMINAL");
        assertThat(body).containsEntry("status", "COMPLETED");
        verify(bootstrapJobRepository, never()).transitionStatus(any(), any(), any());
    }

    // ── DELETE 404 on unknown job ────────────────────────────────────────────

    @Test
    void cancel_unknownJob_returns404() {
        when(bootstrapJobRepository.cancelIfActive(JOB_ID, TENANT_ID)).thenReturn(0);
        when(bootstrapJobRepository.findTenantForJob(JOB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.cancel(JOB_ID, adminJwt()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── (j) Tier-gate: controller class carries @TierRequired(PRO_EPR) ────────
    //
    // The TierGateInterceptor runs as a Spring MVC interceptor, so it is bypassed
    // in this unit-scope test (we instantiate the controller directly). Verifying
    // the annotation presence ensures the interceptor will return 402 for non-PRO_EPR
    // tenants at runtime. The TierGateIntegrationTest covers the end-to-end 402.

    @Test
    void controller_carriesProEprTierRequired() {
        TierRequired annotation = BootstrapJobController.class.getAnnotation(TierRequired.class);
        assertThat(annotation)
                .as("@TierRequired(PRO_EPR) must be present so TierGateInterceptor enforces tier at runtime")
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(Tier.PRO_EPR);
    }

    // ── Exception handler: 412 with structured { code, message } body ────────

    @Test
    void preconditionException_rendersStructuredBody() {
        BootstrapPreconditionException ex = new BootstrapPreconditionException(
                HttpStatus.PRECONDITION_FAILED, "NAV_CREDENTIALS_MISSING", "NAV credentials missing");

        ResponseEntity<java.util.Map<String, Object>> response = controller.handlePrecondition(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(response.getBody()).containsEntry("code", "NAV_CREDENTIALS_MISSING");
        assertThat(response.getBody()).containsEntry("message", "NAV credentials missing");
    }

    // ── Exception handler: 409 ALREADY_RUNNING body carries jobId ────────────

    @Test
    void preconditionException_alreadyRunning_bodyCarriesJobId() {
        BootstrapPreconditionException ex = BootstrapPreconditionException.alreadyRunning(JOB_ID);

        ResponseEntity<java.util.Map<String, Object>> response = controller.handlePrecondition(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "ALREADY_RUNNING");
        assertThat(response.getBody()).containsEntry("jobId", JOB_ID);
    }

    // ── (k) Role gate: GUEST → 403 ────────────────────────────────────────────

    @Test
    void trigger_guestRole_throws403() {
        Jwt guestJwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("guest@test.com")
                .claim("active_tenant_id", TENANT_ID.toString())
                .claim("user_id", USER_ID.toString())
                .claim("role", "GUEST")
                .build();

        assertThatThrownBy(() -> controller.trigger(null, guestJwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Jwt adminJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin@test.com")
                .claim("active_tenant_id", TENANT_ID.toString())
                .claim("user_id", USER_ID.toString())
                .claim("role", "SME_ADMIN")
                .build();
    }

    private BootstrapJobRecord buildJobRecord(BootstrapJobStatus status) {
        return new BootstrapJobRecord(JOB_ID, TENANT_ID, status,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                10, 8, 1, 1, 8, 0, USER_ID, null,
                OffsetDateTime.now(), OffsetDateTime.now(), null);
    }
}
