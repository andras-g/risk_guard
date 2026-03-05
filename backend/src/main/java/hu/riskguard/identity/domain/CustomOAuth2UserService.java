package hu.riskguard.identity.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String subject = oAuth2User.getAttribute("sub"); // Google/MS unique ID
        String provider = userRequest.getClientRegistration().getRegistrationId();

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        userRepository.findByEmail(email).orElseGet(() -> createNewUserAndTenant(email, name, provider, subject));

        return oAuth2User;
    }

    private User createNewUserAndTenant(String email, String name, String provider, String subject) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(name != null ? name + "'s Tenant" : email + "'s Tenant");
        tenantRepository.save(tenant);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setName(name);
        user.setSsoProvider(provider);
        user.setSsoSubject(subject);
        user.setRole("SME_ADMIN");
        return userRepository.save(user);
    }
}
