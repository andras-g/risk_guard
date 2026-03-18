package hu.riskguard.identity.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.CustomOAuth2User;
import hu.riskguard.identity.internal.IdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final IdentityRepository identityRepository;
    private final RiskGuardProperties properties;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        User user = processOAuth2User(userRequest, oAuth2User);
        String tier = identityRepository.findTenantTier(user.getTenantId())
                .orElse(properties.getIdentity().getDefaultTier());
        return new CustomOAuth2User(oAuth2User, user.getId(), user.getTenantId(), user.getRole(), tier);
    }

    User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String subject = oAuth2User.getAttribute("sub");
        String provider = userRequest.getClientRegistration().getRegistrationId();

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        return identityRepository.findUserByEmail(email)
                .orElseGet(() -> provisionNewUserAndTenant(email, name, provider, subject));
    }

    private static final int MAX_NAME_LENGTH = 100;

    /**
     * Sanitizes the name attribute from OAuth2 providers.
     * Trims whitespace, strips control characters, and truncates to MAX_NAME_LENGTH.
     * Returns null if the result is blank or the input is null.
     */
    static String sanitizeOAuth2Name(String name) {
        if (name == null) {
            return null;
        }
        // Strip control characters (Unicode category Cc) and trim
        String sanitized = name.replaceAll("\\p{Cc}", "").trim();
        if (sanitized.isBlank()) {
            return null;
        }
        if (sanitized.length() > MAX_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_NAME_LENGTH);
        }
        return sanitized;
    }

    private User provisionNewUserAndTenant(String email, String name, String provider, String subject) {
        OffsetDateTime now = OffsetDateTime.now();
        String sanitizedName = sanitizeOAuth2Name(name);

        // Create Tenant
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(sanitizedName != null ? sanitizedName + "'s Tenant" : email + "'s Tenant");
        tenant.setTier(properties.getIdentity().getDefaultTier());
        tenant.setCreatedAt(now);
        identityRepository.saveTenant(tenant);

        // Create User
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setName(sanitizedName);
        user.setSsoProvider(provider);
        user.setSsoSubject(subject);
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
}
