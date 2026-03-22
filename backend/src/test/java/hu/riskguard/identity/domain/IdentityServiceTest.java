package hu.riskguard.identity.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.internal.IdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IdentityService} — getActiveMandateTenantIds (Story 3.9).
 * Pure Mockito — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GuestSessionService guestSessionService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private IdentityService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RiskGuardProperties properties = new RiskGuardProperties();
        service = new IdentityService(identityRepository, passwordEncoder, properties, guestSessionService, refreshTokenService);
    }

    @Test
    void getActiveMandateTenantIds_returnsActiveMandates() {
        when(identityRepository.findActiveMandateTenantIds(eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(List.of(TENANT_A, TENANT_B));

        List<UUID> result = service.getActiveMandateTenantIds(USER_ID);

        assertThat(result).containsExactly(TENANT_A, TENANT_B);
        verify(identityRepository).findActiveMandateTenantIds(eq(USER_ID), any(OffsetDateTime.class));
    }

    @Test
    void getActiveMandateTenantIds_excludesExpiredMandates() {
        // Repository is responsible for date filtering; we verify the call passes through
        when(identityRepository.findActiveMandateTenantIds(eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(List.of(TENANT_A)); // Only A is active, B expired

        List<UUID> result = service.getActiveMandateTenantIds(USER_ID);

        assertThat(result).containsExactly(TENANT_A);
        assertThat(result).doesNotContain(TENANT_B);
    }

    @Test
    void getActiveMandateTenantIds_returnsEmptyForNoMandates() {
        when(identityRepository.findActiveMandateTenantIds(eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        List<UUID> result = service.getActiveMandateTenantIds(USER_ID);

        assertThat(result).isEmpty();
    }
}
