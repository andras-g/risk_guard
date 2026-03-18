package hu.riskguard.screening.api;

import hu.riskguard.jooq.enums.VerdictConfidence;
import hu.riskguard.jooq.enums.VerdictStatus;
import hu.riskguard.screening.api.dto.PartnerSearchRequest;
import hu.riskguard.screening.api.dto.ProvenanceResponse;
import hu.riskguard.screening.api.dto.VerdictResponse;
import hu.riskguard.screening.domain.ScreeningService;
import hu.riskguard.screening.domain.ScreeningService.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningControllerTest {

    @Mock
    private ScreeningService screeningService;

    private ScreeningController controller;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new ScreeningController(screeningService);
    }

    @Test
    void searchWithValidTaxNumberShouldReturnVerdictResponse() {
        // Given
        String taxNumber = "12345678";
        PartnerSearchRequest request = new PartnerSearchRequest(taxNumber);
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);

        SearchResult searchResult = new SearchResult(
                UUID.randomUUID(), UUID.randomUUID(), taxNumber,
                VerdictStatus.RELIABLE, VerdictConfidence.FRESH, OffsetDateTime.now(),
                List.of(), false, "Test Company Kft.", "abc123hash"
        );
        when(screeningService.search(eq(taxNumber), eq(USER_ID), eq(TENANT_ID))).thenReturn(searchResult);

        // When
        VerdictResponse result = controller.search(request, jwt);

        // Then
        assertThat(result.taxNumber()).isEqualTo(taxNumber);
        assertThat(result.status()).isEqualTo("RELIABLE");
        assertThat(result.confidence()).isEqualTo("FRESH");
        assertThat(result.riskSignals()).isEmpty();
        assertThat(result.cached()).isFalse();
        assertThat(result.companyName()).isEqualTo("Test Company Kft.");
        assertThat(result.sha256Hash()).isEqualTo("abc123hash");
        verify(screeningService).search(taxNumber, USER_ID, TENANT_ID);
    }

    @Test
    void searchWith11DigitTaxNumberShouldReturnVerdictResponse() {
        // Given
        String taxNumber = "12345678901";
        PartnerSearchRequest request = new PartnerSearchRequest(taxNumber);
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);

        SearchResult searchResult = new SearchResult(
                UUID.randomUUID(), UUID.randomUUID(), taxNumber,
                VerdictStatus.AT_RISK, VerdictConfidence.STALE, OffsetDateTime.now(),
                List.of("PUBLIC_DEBT_DETECTED"), false, null, null
        );
        when(screeningService.search(eq(taxNumber), eq(USER_ID), eq(TENANT_ID))).thenReturn(searchResult);

        // When
        VerdictResponse result = controller.search(request, jwt);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("AT_RISK");
        assertThat(result.confidence()).isEqualTo("STALE");
        assertThat(result.riskSignals()).containsExactly("PUBLIC_DEBT_DETECTED");
    }

    @Test
    void searchShouldIncludeRiskSignalsArrayInResponse() {
        // Given — AT_RISK verdict with multiple risk signals
        String taxNumber = "12345678";
        PartnerSearchRequest request = new PartnerSearchRequest(taxNumber);
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);

        SearchResult searchResult = new SearchResult(
                UUID.randomUUID(), UUID.randomUUID(), taxNumber,
                VerdictStatus.AT_RISK, VerdictConfidence.FRESH, OffsetDateTime.now(),
                List.of("PUBLIC_DEBT_DETECTED", "SOURCE_UNAVAILABLE:nav-debt"), false, null, null
        );
        when(screeningService.search(eq(taxNumber), eq(USER_ID), eq(TENANT_ID))).thenReturn(searchResult);

        // When
        VerdictResponse result = controller.search(request, jwt);

        // Then — riskSignals array is correctly propagated
        assertThat(result.riskSignals()).containsExactly("PUBLIC_DEBT_DETECTED", "SOURCE_UNAVAILABLE:nav-debt");
        assertThat(result.status()).isEqualTo("AT_RISK");
    }

    @Test
    void searchShouldRejectMissingUserId() {
        // Given — JWT without user_id claim
        PartnerSearchRequest request = new PartnerSearchRequest("12345678");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", TENANT_ID.toString())
                .build();

        // When / Then
        assertThatThrownBy(() -> controller.search(request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("user_id");
    }

    @Test
    void searchShouldRejectMissingTenantId() {
        // Given — JWT without active_tenant_id claim
        PartnerSearchRequest request = new PartnerSearchRequest("12345678");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("user_id", USER_ID.toString())
                .build();

        // When / Then
        assertThatThrownBy(() -> controller.search(request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active_tenant_id");
    }

    @Test
    void searchShouldRejectMalformedUuidInJwtClaims() {
        // Given — JWT with non-UUID user_id claim
        PartnerSearchRequest request = new PartnerSearchRequest("12345678");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("user_id", "not-a-valid-uuid")
                .claim("active_tenant_id", TENANT_ID.toString())
                .build();

        // When / Then — should get 401 (not 500)
        assertThatThrownBy(() -> controller.search(request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not a valid UUID");
    }

    // --- Story 2.5: sha256Hash paths ---

    @Test
    void searchShouldPropagateHashUnavailableSentinelInResponse() {
        // Given — search result with HASH_UNAVAILABLE sentinel (hash computation failed)
        String taxNumber = "12345678";
        PartnerSearchRequest request = new PartnerSearchRequest(taxNumber);
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);

        SearchResult searchResult = new SearchResult(
                UUID.randomUUID(), UUID.randomUUID(), taxNumber,
                VerdictStatus.RELIABLE, VerdictConfidence.FRESH, OffsetDateTime.now(),
                List.of(), false, "Test Company Kft.", "HASH_UNAVAILABLE"
        );
        when(screeningService.search(eq(taxNumber), eq(USER_ID), eq(TENANT_ID))).thenReturn(searchResult);

        // When
        VerdictResponse result = controller.search(request, jwt);

        // Then — sentinel value propagated as-is
        assertThat(result.sha256Hash()).isEqualTo("HASH_UNAVAILABLE");
    }

    @Test
    void cachedSearchShouldReturnNullSha256HashInResponse() {
        // Given — cached result (idempotency guard hit) — sha256Hash is null
        String taxNumber = "12345678";
        PartnerSearchRequest request = new PartnerSearchRequest(taxNumber);
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);

        SearchResult searchResult = new SearchResult(
                UUID.randomUUID(), UUID.randomUUID(), taxNumber,
                VerdictStatus.RELIABLE, VerdictConfidence.FRESH, OffsetDateTime.now(),
                List.of(), true, null, null  // cached=true, sha256Hash=null
        );
        when(screeningService.search(eq(taxNumber), eq(USER_ID), eq(TENANT_ID))).thenReturn(searchResult);

        // When
        VerdictResponse result = controller.search(request, jwt);

        // Then — null sha256Hash for cached results
        assertThat(result.cached()).isTrue();
        assertThat(result.sha256Hash()).isNull();
    }

    // --- Provenance endpoint tests (Story 2.4) ---

    @Test
    void getSnapshotProvenanceShouldReturnProvenanceForValidSnapshot() {
        // Given
        UUID snapshotId = UUID.randomUUID();
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);

        ProvenanceResponse expected = new ProvenanceResponse(
                snapshotId, "12345678", OffsetDateTime.now(),
                List.of(
                        new ProvenanceResponse.SourceProvenance("demo", true, OffsetDateTime.now(), null)
                )
        );
        when(screeningService.getSnapshotProvenance(eq(snapshotId)))
                .thenReturn(Optional.of(expected));

        // When
        ProvenanceResponse result = controller.getSnapshotProvenance(snapshotId, jwt);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.snapshotId()).isEqualTo(snapshotId);
        assertThat(result.taxNumber()).isEqualTo("12345678");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().getFirst().sourceName()).isEqualTo("demo");
        assertThat(result.sources().getFirst().available()).isTrue();
        verify(screeningService).getSnapshotProvenance(snapshotId);
    }

    @Test
    void getSnapshotProvenanceShouldReturn404ForUnknownSnapshot() {
        // Given
        UUID unknownSnapshotId = UUID.randomUUID();
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);

        when(screeningService.getSnapshotProvenance(eq(unknownSnapshotId)))
                .thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> controller.getSnapshotProvenance(unknownSnapshotId, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void getSnapshotProvenanceShouldRejectMissingTenantId() {
        // Given — JWT without active_tenant_id claim
        UUID snapshotId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("user_id", USER_ID.toString())
                .build();

        // When / Then
        assertThatThrownBy(() -> controller.getSnapshotProvenance(snapshotId, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active_tenant_id");
    }

    private Jwt buildJwt(UUID userId, UUID tenantId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("user_id", userId.toString())
                .claim("active_tenant_id", tenantId.toString())
                .claim("role", "SME_ADMIN")
                .build();
    }
}
