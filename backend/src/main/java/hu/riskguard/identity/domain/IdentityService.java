package hu.riskguard.identity.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.api.dto.TenantResponse;
import hu.riskguard.identity.internal.IdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service facade for identity operations.
 * Exposes IdentityRepository methods through the domain layer,
 * so API controllers don't directly depend on internal package.
 *
 * <p>All read methods are annotated {@code @Transactional(readOnly = true)} to:
 * <ul>
 *   <li>Ensure consistent snapshot reads (no phantom reads between multiple queries in one request).</li>
 *   <li>Prevent TOCTOU issues (e.g., user deleted between {@code findUserByEmail} and {@code hasMandate}).</li>
 *   <li>Allow the JPA/JDBC layer to optimize for read-only connections.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final IdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final RiskGuardProperties properties;
    private final GuestSessionService guestSessionService;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return identityRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<String> findSsoProviderByEmail(String email) {
        return identityRepository.findSsoProviderByEmail(email);
    }

    /**
     * Register a new local (email/password) user.
     * Creates a tenant + user + self-mandate, mirroring the SSO provisioning flow.
     */
    @Transactional
    public User registerLocalUser(String email, String password, String name) {
        OffsetDateTime now = OffsetDateTime.now();

        // Create Tenant
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(name + "'s Tenant");
        tenant.setTier(properties.getIdentity().getDefaultTier());
        tenant.setCreatedAt(now);
        identityRepository.saveTenant(tenant);

        // Create User
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setName(name);
        user.setSsoProvider("local");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(properties.getIdentity().getDefaultUserRole());
        user.setPreferredLanguage(properties.getIdentity().getDefaultLanguage());
        user.setCreatedAt(now);
        User savedUser = identityRepository.saveUser(user);

        // Create Initial Mandate (Self-access)
        TenantMandate mandate = new TenantMandate();
        mandate.setId(UUID.randomUUID());
        mandate.setAccountantUserId(savedUser.getId());
        mandate.setTenantId(tenant.getId());
        mandate.setValidFrom(now);
        identityRepository.saveTenantMandate(mandate);

        return savedUser;
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserByEmail(String email) {
        return identityRepository.findUserByEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean hasMandate(UUID userId, UUID tenantId) {
        return identityRepository.hasMandate(userId, tenantId);
    }

    @Transactional
    public void updatePreferredLanguage(UUID userId, String language) {
        identityRepository.updatePreferredLanguage(userId, language);
    }

    /**
     * Returns the tier string for a tenant, or null if not found.
     * Used by TierGateInterceptor for tier enforcement.
     */
    @Transactional(readOnly = true)
    public String findTenantTier(UUID tenantId) {
        return identityRepository.findTenantTier(tenantId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> findMandatedTenants(UUID userId) {
        return identityRepository.findMandatedTenants(userId).stream()
                .map(TenantResponse::from)
                .toList();
    }

    /**
     * Get all tenant IDs where a user has currently active mandates.
     * Used by notification module's PortfolioController to aggregate alerts across all
     * mandated tenants for an accountant's Portfolio Pulse feed.
     *
     * @param userId the accountant's user ID
     * @return list of tenant UUIDs with active mandates (may be empty)
     */
    @Transactional(readOnly = true)
    public List<UUID> getActiveMandateTenantIds(UUID userId) {
        return identityRepository.findActiveMandateTenantIds(userId, OffsetDateTime.now());
    }

    /**
     * Get a user's email address by user ID — used by OutboxProcessor for recipient resolution.
     * Returns null if user not found.
     *
     * @param userId the user ID
     * @return the user's email address, or null if not found
     */
    @Transactional(readOnly = true)
    public String getUserEmail(UUID userId) {
        return identityRepository.findEmailById(userId).orElse(null);
    }

    /**
     * Get a user's preferred language by user ID — used by EmailTemplateRenderer for localization.
     * Returns the default language from configuration if user not found.
     *
     * @param userId the user ID
     * @return the user's preferred language code (e.g., "hu", "en")
     */
    @Transactional(readOnly = true)
    public String getUserPreferredLanguage(UUID userId) {
        return identityRepository.findPreferredLanguageById(userId)
                .orElse(properties.getIdentity().getDefaultLanguage());
    }

    // ─── Refresh Token Facade (Story 3.13) ─────────────────────────────────────

    /**
     * Issue a new refresh token for a user (new login session → new family).
     * @return the raw opaque token to set as HttpOnly cookie
     */
    @Transactional
    public String issueRefreshToken(UUID userId, UUID tenantId) {
        return refreshTokenService.issueRefreshToken(userId, tenantId);
    }

    /**
     * Validate and rotate a refresh token. Handles reuse detection and family revocation.
     * Uses SERIALIZABLE-equivalent protection via SELECT ... FOR UPDATE in the repository.
     */
    @Transactional
    public RefreshTokenService.RotationResult rotateRefreshToken(String rawToken) {
        return refreshTokenService.validateAndRotate(rawToken);
    }

    /**
     * Revoke a single refresh token (e.g., on logout).
     */
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        refreshTokenService.revokeToken(rawToken);
    }

    /**
     * Revoke ALL refresh tokens for a user (e.g., admin session termination).
     */
    @Transactional
    public void revokeAllUserSessions(UUID userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    // ─── Guest Session Facade (Story 3.12) ──────────────────────────────────────

    /**
     * Find or create a guest session by fingerprint. Delegates to GuestSessionService.
     */
    @Transactional
    public GuestSession findOrCreateGuestSession(String sessionFingerprint) {
        return guestSessionService.findOrCreateSession(sessionFingerprint);
    }

    /**
     * Check guest session rate limits. Delegates to GuestSessionService.
     */
    @Transactional(readOnly = true)
    public GuestLimitStatus checkGuestLimits(GuestSession session) {
        return guestSessionService.checkLimits(session);
    }

    /**
     * Increment guest session counters after a successful search.
     */
    @Transactional
    public void incrementGuestCounters(UUID sessionId, boolean isNewCompany) {
        guestSessionService.incrementCounters(sessionId, isNewCompany);
    }

    /**
     * Get guest limit configuration values.
     */
    public int getGuestMaxCompanies() {
        return guestSessionService.getMaxCompanies();
    }

    public int getGuestMaxDailyChecks() {
        return guestSessionService.getMaxDailyChecks();
    }
}
