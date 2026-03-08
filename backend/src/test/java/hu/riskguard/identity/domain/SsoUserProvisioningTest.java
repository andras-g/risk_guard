package hu.riskguard.identity.domain;

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

/**
 * Tests SSO user and tenant provisioning logic against a real database (Testcontainers).
 * The OAuth2UserRequest is mocked because it requires a real OAuth2 access token,
 * but the provisioning logic (DB writes, tenant creation, mandate creation) runs
 * against a real PostgreSQL database. This is NOT an end-to-end OAuth2 flow test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SsoUserProvisioningTest {

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private IdentityRepository identityRepository;

    @Test
    void shouldProvisionUserAndTenantOnFirstGoogleLogin() {
        // Given
        String email = "test-sso-provisioning@riskguard.hu";
        String name = "SSO Provisioning Test User";

        Map<String, Object> attributes = Map.of(
                "email", email,
                "name", name,
                "sub", "sso-sub-123"
        );

        // OAuth2UserRequest must be mocked — it requires a real OAuth2 access token.
        // Only getClientRegistration() is needed by our CustomOAuth2UserService.
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

        // When
        customOAuth2UserService.processOAuth2User(userRequest, oAuth2User);

        // Then
        Optional<User> userOpt = identityRepository.findUserByEmail(email);
        assertThat(userOpt).isPresent();
        User user = userOpt.get();
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getTenantId()).isNotNull();
        assertThat(user.getPreferredLanguage()).isEqualTo("hu");
        assertThat(user.getRole()).isEqualTo("SME_ADMIN");
        assertThat(user.getSsoProvider()).isEqualTo("google");
    }
}
