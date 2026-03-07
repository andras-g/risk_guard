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
        return new CustomOAuth2User(oAuth2User, user.getId(), user.getTenantId());
    }

    public User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
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

    private User provisionNewUserAndTenant(String email, String name, String provider, String subject) {
        OffsetDateTime now = OffsetDateTime.now();

        // Create Tenant
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(name != null ? name + "'s Tenant" : email + "'s Tenant");
        tenant.setTier(properties.getIdentity().getDefaultTier());
        tenant.setCreatedAt(now);
        identityRepository.saveTenant(tenant);

        // Create User
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setName(name);
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
