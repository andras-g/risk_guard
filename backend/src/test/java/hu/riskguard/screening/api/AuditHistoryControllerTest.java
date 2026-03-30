package hu.riskguard.screening.api;

import hu.riskguard.screening.api.dto.AuditHashVerifyResponse;
import hu.riskguard.screening.api.dto.AuditHistoryPageResponse;
import hu.riskguard.screening.domain.AuditHashVerifyResult;
import hu.riskguard.screening.domain.AuditHistoryEntry;
import hu.riskguard.screening.domain.AuditHistoryFilter;
import hu.riskguard.screening.domain.ScreeningService;
import hu.riskguard.screening.domain.ScreeningService.AuditHistoryPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.ErrorResponseException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditHistoryControllerTest {

    @Mock
    private ScreeningService screeningService;

    private AuditHistoryController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID AUDIT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new AuditHistoryController(screeningService);
    }

    // ─── getAuditHistory ─────────────────────────────────────────────────────

    @Test
    void getAuditHistory_returnsPageResponse() {
        // Given
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);
        AuditHistoryEntry entry = new AuditHistoryEntry(
                AUDIT_ID, "Test Kft.", "12345678",
                "RELIABLE", "FRESH", OffsetDateTime.now(),
                "abc123hash", "LIVE", "MANUAL",
                List.of("https://nav.gov.hu"), "Disclaimer text");
        AuditHistoryPage page = new AuditHistoryPage(List.of(entry), 1L, 0, 20);
        when(screeningService.getAuditHistory(any(AuditHistoryFilter.class), eq(0), eq(20))).thenReturn(page);

        // When
        AuditHistoryPageResponse response = controller.getAuditHistory(
                0, 20, null, null, null, null, null, jwt);

        // Then
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).taxNumber()).isEqualTo("12345678");
        assertThat(response.content().get(0).checkSource()).isEqualTo("MANUAL");
        assertThat(response.content().get(0).dataSourceMode()).isEqualTo("LIVE");
    }

    @Test
    void getAuditHistory_emptytResult_returnsEmptyPage() {
        // Given
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);
        AuditHistoryPage emptyPage = new AuditHistoryPage(List.of(), 0L, 0, 20);
        when(screeningService.getAuditHistory(any(), eq(0), eq(20))).thenReturn(emptyPage);

        // When
        AuditHistoryPageResponse response = controller.getAuditHistory(
                0, 20, null, null, null, null, null, jwt);

        // Then
        assertThat(response.totalElements()).isZero();
        assertThat(response.content()).isEmpty();
    }

    @Test
    void getAuditHistory_clampsSizeAboveMaximum() {
        // Given — size 999 should be clamped to 100
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);
        AuditHistoryPage page = new AuditHistoryPage(List.of(), 0L, 0, 100);
        when(screeningService.getAuditHistory(any(), eq(0), eq(100))).thenReturn(page);

        // When — no exception
        AuditHistoryPageResponse response = controller.getAuditHistory(
                0, 999, null, null, null, null, null, jwt);

        assertThat(response.size()).isEqualTo(100);
    }

    @Test
    void getAuditHistory_missingTenantClaim_returns401() {
        // Given — JWT has no active_tenant_id
        Jwt jwt = buildJwtWithoutTenant(USER_ID);

        // When / Then
        assertThatThrownBy(() -> controller.getAuditHistory(
                0, 20, null, null, null, null, null, jwt))
                .isInstanceOf(ErrorResponseException.class)
                .satisfies(e -> assertThat(((ErrorResponseException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ─── verifyHash ──────────────────────────────────────────────────────────

    @Test
    void verifyHash_matchingHash_returnsMatchTrue() {
        // Given
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);
        AuditHashVerifyResult result = new AuditHashVerifyResult(true, "abc123", "abc123", false);
        when(screeningService.verifyAuditHash(AUDIT_ID)).thenReturn(Optional.of(result));

        // When
        AuditHashVerifyResponse response = controller.verifyHash(AUDIT_ID, jwt);

        // Then
        assertThat(response.match()).isTrue();
        assertThat(response.unavailable()).isFalse();
        assertThat(response.computedHash()).isEqualTo("abc123");
        assertThat(response.storedHash()).isEqualTo("abc123");
    }

    @Test
    void verifyHash_mismatchedHash_returnsMatchFalse() {
        // Given
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);
        AuditHashVerifyResult result = new AuditHashVerifyResult(false, "computed99", "stored00", true);
        when(screeningService.verifyAuditHash(AUDIT_ID)).thenReturn(Optional.of(result));

        // When
        AuditHashVerifyResponse response = controller.verifyHash(AUDIT_ID, jwt);

        // Then
        assertThat(response.match()).isFalse();
        assertThat(response.unavailable()).isTrue();
        assertThat(response.computedHash()).isEqualTo("computed99");
        assertThat(response.storedHash()).isEqualTo("stored00");
    }

    @Test
    void verifyHash_entryNotFound_returns404() {
        // Given
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);
        when(screeningService.verifyAuditHash(AUDIT_ID)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> controller.verifyHash(AUDIT_ID, jwt))
                .isInstanceOf(ErrorResponseException.class)
                .satisfies(e -> assertThat(((ErrorResponseException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void verifyHash_missingTenantClaim_returns401() {
        // Given — JWT has no active_tenant_id
        Jwt jwt = buildJwtWithoutTenant(USER_ID);

        // When / Then
        assertThatThrownBy(() -> controller.verifyHash(AUDIT_ID, jwt))
                .isInstanceOf(ErrorResponseException.class)
                .satisfies(e -> assertThat(((ErrorResponseException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verifyHash_sentinelHash_returnsUnavailable() {
        // Given — sentinel stored hash should return unavailable, not match=true
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);
        AuditHashVerifyResult result = new AuditHashVerifyResult(false, "HASH_UNAVAILABLE", "HASH_UNAVAILABLE", true);
        when(screeningService.verifyAuditHash(AUDIT_ID)).thenReturn(Optional.of(result));

        // When
        AuditHashVerifyResponse response = controller.verifyHash(AUDIT_ID, jwt);

        // Then — sentinel must never produce match=true
        assertThat(response.match()).isFalse();
        assertThat(response.unavailable()).isTrue();
    }

    @Test
    void getAuditHistory_invalidCheckSource_returns400() {
        // Given — lowercase "manual" is not in the allowlist
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);

        // When / Then
        assertThatThrownBy(() -> controller.getAuditHistory(
                0, 20, null, null, null, null, "manual", jwt))
                .isInstanceOf(ErrorResponseException.class)
                .satisfies(e -> assertThat(((ErrorResponseException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getAuditHistory_validCheckSourceManual_accepted() {
        // Given — "MANUAL" is a valid value
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);
        AuditHistoryPage page = new AuditHistoryPage(List.of(), 0L, 0, 20);
        when(screeningService.getAuditHistory(any(), eq(0), eq(20))).thenReturn(page);

        // When / Then — no exception thrown
        AuditHistoryPageResponse response = controller.getAuditHistory(
                0, 20, null, null, null, null, "MANUAL", jwt);
        assertThat(response).isNotNull();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Jwt buildJwt(UUID userId, UUID tenantId) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("user_id", userId.toString())
                .claim("active_tenant_id", tenantId.toString())
                .build();
    }

    private Jwt buildJwtWithoutTenant(UUID userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("user_id", userId.toString())
                .build();
    }
}
