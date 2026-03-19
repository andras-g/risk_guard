package hu.riskguard.notification.api;

import hu.riskguard.notification.api.dto.AddWatchlistEntryRequest;
import hu.riskguard.notification.api.dto.AddWatchlistEntryResponse;
import hu.riskguard.notification.api.dto.WatchlistCountResponse;
import hu.riskguard.notification.api.dto.WatchlistEntryResponse;
import hu.riskguard.notification.domain.NotificationService;
import hu.riskguard.notification.domain.NotificationService.AddResult;
import hu.riskguard.notification.domain.WatchlistEntry;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WatchlistController}.
 * Follows the same pattern as ScreeningControllerTest — pure Mockito, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class WatchlistControllerTest {

    @Mock
    private NotificationService notificationService;

    private WatchlistController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new WatchlistController(notificationService);
    }

    @Test
    void addEntryShouldDelegateToServiceAndReturnResponse() {
        String taxNumber = "12345678";
        AddWatchlistEntryRequest request = new AddWatchlistEntryRequest(taxNumber, "Test Company Kft.", "RELIABLE");
        Jwt jwt = buildJwt(TENANT_ID);

        WatchlistEntry entry = buildEntry(taxNumber);
        when(notificationService.addToWatchlist(eq(TENANT_ID), eq(taxNumber), eq("Test Company Kft."), eq("RELIABLE")))
                .thenReturn(new AddResult(entry, false));

        AddWatchlistEntryResponse result = controller.addEntry(request, jwt);

        assertThat(result.entry().taxNumber()).isEqualTo(taxNumber);
        assertThat(result.entry().id()).isNotNull();
        assertThat(result.duplicate()).isFalse();
        verify(notificationService).addToWatchlist(eq(TENANT_ID), eq(taxNumber), eq("Test Company Kft."), eq("RELIABLE"));
    }

    @Test
    void listEntriesShouldReturnAllEntriesForTenant() {
        Jwt jwt = buildJwt(TENANT_ID);
        List<WatchlistEntry> entries = List.of(
                buildEntry("12345678"), buildEntry("99887766"));
        when(notificationService.getWatchlistEntries(eq(TENANT_ID))).thenReturn(entries);

        List<WatchlistEntryResponse> result = controller.listEntries(jwt);

        assertThat(result).hasSize(2);
        verify(notificationService).getWatchlistEntries(TENANT_ID);
    }

    @Test
    void removeEntryShouldReturn204WhenDeleted() {
        Jwt jwt = buildJwt(TENANT_ID);
        UUID entryId = UUID.randomUUID();
        when(notificationService.removeFromWatchlist(eq(TENANT_ID), eq(entryId))).thenReturn(true);

        controller.removeEntry(entryId, jwt);

        verify(notificationService).removeFromWatchlist(TENANT_ID, entryId);
    }

    @Test
    void removeEntryShouldReturn404WhenNotFound() {
        Jwt jwt = buildJwt(TENANT_ID);
        UUID entryId = UUID.randomUUID();
        when(notificationService.removeFromWatchlist(eq(TENANT_ID), eq(entryId))).thenReturn(false);

        assertThatThrownBy(() -> controller.removeEntry(entryId, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void getCountShouldReturnEntryCount() {
        Jwt jwt = buildJwt(TENANT_ID);
        when(notificationService.getWatchlistCount(eq(TENANT_ID))).thenReturn(5);

        WatchlistCountResponse result = controller.getCount(jwt);

        assertThat(result.count()).isEqualTo(5);
        verify(notificationService).getWatchlistCount(TENANT_ID);
    }

    @Test
    void addEntryShouldRejectMissingTenantId() {
        AddWatchlistEntryRequest request = new AddWatchlistEntryRequest("12345678", null, null);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .build();

        assertThatThrownBy(() -> controller.addEntry(request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active_tenant_id");
    }

    @Test
    void listEntriesShouldRejectMalformedTenantId() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", "not-a-uuid")
                .build();

        assertThatThrownBy(() -> controller.listEntries(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not a valid UUID");
    }

    private Jwt buildJwt(UUID tenantId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", tenantId.toString())
                .claim("role", "SME_ADMIN")
                .build();
    }

    private WatchlistEntry buildEntry(String taxNumber) {
        return new WatchlistEntry(
                UUID.randomUUID(), TENANT_ID, taxNumber, "Test Company Kft.",
                null, OffsetDateTime.now(), OffsetDateTime.now());
    }
}
