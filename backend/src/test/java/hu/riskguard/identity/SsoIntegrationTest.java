package hu.riskguard.identity;

import hu.riskguard.identity.domain.CustomOAuth2UserService;
import hu.riskguard.identity.domain.User;
import hu.riskguard.identity.internal.IdentityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SsoIntegrationTest {

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private IdentityRepository identityRepository;

    @Test
    void shouldProvisionUserAndTenantOnSuccessfulSso() {
        String email = "test-sso-integration@riskguard.hu";
        String name = "SSO Integration Test User";

        Map<String, Object> attributes = Map.of(
                "email", email,
                "name", name,
                "sub", "sso-sub-123"
        );

        OAuth2UserRequest userRequest = mock(OAuth2UserRequest.class);
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("id")
                .tokenUri("uri")
                .authorizationUri("uri")
                .redirectUri("uri")
                .build();

        when(userRequest.getClientRegistration()).thenReturn(clientRegistration);

        OAuth2User oAuth2User = new DefaultOAuth2User(Collections.emptyList(), attributes, "sub");

        customOAuth2UserService.processOAuth2User(userRequest, oAuth2User);

        Optional<User> userOpt = identityRepository.findUserByEmail(email);
        assertThat(userOpt).isPresent();
        User user = userOpt.get();
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getTenantId()).isNotNull();
        assertThat(user.getPreferredLanguage()).isEqualTo("hu");
    }
}
