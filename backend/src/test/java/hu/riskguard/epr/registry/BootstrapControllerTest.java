package hu.riskguard.epr.registry;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.epr.registry.api.RegistryBootstrapController;
import hu.riskguard.epr.registry.api.dto.*;
import hu.riskguard.epr.registry.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RegistryBootstrapController}.
 */
@ExtendWith(MockitoExtension.class)
class BootstrapControllerTest {

    @Mock
    private RegistryBootstrapService bootstrapService;

    private RegistryBootstrapController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CANDIDATE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new RegistryBootstrapController(bootstrapService);
    }

    // ─── Test 1: POST /bootstrap → 201 Created ───────────────────────────────

    @Test
    void trigger_happyPath_returns201WithCreatedCount() {
        when(bootstrapService.triggerBootstrap(eq(TENANT_ID), eq(USER_ID), any(), any()))
                .thenReturn(new BootstrapResult(5, 2));

        BootstrapResultResponse result = controller.trigger(
                new BootstrapTriggerRequest(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)),
                buildJwt());

        assertThat(result.created()).isEqualTo(5);
        assertThat(result.skipped()).isEqualTo(2);
    }

    // ─── Test 2: GET /candidates returns paginated list ───────────────────────

    @Test
    void listCandidates_happyPath_returnsPaginatedResponse() {
        BootstrapCandidate candidate = buildCandidate(CANDIDATE_ID, BootstrapCandidateStatus.PENDING);
        BootstrapCandidatesPage page = new BootstrapCandidatesPage(
                List.of(candidate), 1L, 0, 50);

        when(bootstrapService.listCandidates(eq(TENANT_ID), any(), eq(0), eq(50)))
                .thenReturn(page);

        BootstrapCandidatesPageResponse result = controller.listCandidates(null, 0, 50, buildJwt());

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(CANDIDATE_ID);
    }

    // ─── Test 3: page size clamped at 200 ────────────────────────────────────

    @Test
    void listCandidates_pageSizeOver200_clampsTo200() {
        BootstrapCandidatesPage page = new BootstrapCandidatesPage(List.of(), 0L, 0, 200);
        when(bootstrapService.listCandidates(eq(TENANT_ID), any(), eq(0), eq(200)))
                .thenReturn(page);

        BootstrapCandidatesPageResponse result = controller.listCandidates(null, 0, 9999, buildJwt());

        assertThat(result.size()).isEqualTo(200);
        verify(bootstrapService).listCandidates(eq(TENANT_ID), any(), eq(0), eq(200));
    }

    // ─── Test 4: approve unknown candidate → propagates 404 ─────────────────

    @Test
    void approve_unknownCandidate_propagates404() {
        UUID unknownId = UUID.randomUUID();
        when(bootstrapService.approveCandidateAndCreateProduct(eq(TENANT_ID), eq(unknownId),
                eq(USER_ID), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));

        BootstrapApproveRequest req = buildApproveRequest();
        assertThatThrownBy(() -> controller.approve(unknownId, req, buildJwt()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ─── Test 5: reject → no content ─────────────────────────────────────────

    @Test
    void reject_happyPath_callsServiceWithCorrectStatus() {
        doNothing().when(bootstrapService).rejectCandidate(
                eq(TENANT_ID), eq(CANDIDATE_ID), eq(USER_ID),
                eq(BootstrapCandidateStatus.REJECTED_NOT_OWN_PACKAGING));

        controller.reject(CANDIDATE_ID, new BootstrapRejectRequest("NOT_OWN_PACKAGING"), buildJwt());

        verify(bootstrapService).rejectCandidate(TENANT_ID, CANDIDATE_ID, USER_ID,
                BootstrapCandidateStatus.REJECTED_NOT_OWN_PACKAGING);
    }

    // ─── Test 6: controller class has @TierRequired(PRO_EPR) ─────────────────

    @Test
    void controller_hasProEprTierAnnotation() {
        TierRequired annotation = RegistryBootstrapController.class.getAnnotation(TierRequired.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(Tier.PRO_EPR);
    }

    // ─── Test 7: BootstrapApproveRequest.name has @NotBlank constraint ────────

    @Test
    void approveRequest_name_hasNotBlankConstraint() throws NoSuchFieldException {
        var nameField = BootstrapApproveRequest.class.getDeclaredField("name");
        assertThat(nameField.isAnnotationPresent(jakarta.validation.constraints.NotBlank.class))
                .as("BootstrapApproveRequest.name must be annotated with @NotBlank")
                .isTrue();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Jwt buildJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("active_tenant_id", TENANT_ID.toString())
                .claim("user_id", USER_ID.toString())
                .build();
    }

    private BootstrapCandidate buildCandidate(UUID id, BootstrapCandidateStatus status) {
        return new BootstrapCandidate(id, TENANT_ID, "TERMÉK A", "39239090",
                3, new BigDecimal("300"), "DARAB", status,
                null, null, "NONE", "LOW", null,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    private BootstrapApproveRequest buildApproveRequest() {
        ComponentUpsertRequest comp = new ComponentUpsertRequest(
                null, "PET anyag", "11010101", new BigDecimal("0.1"), 0,
                1, null, null, null, null, null, null, null, null);
        return new BootstrapApproveRequest("ART-001", "Termék A", "39239090",
                "DARAB", ProductStatus.ACTIVE, List.of(comp));
    }
}
