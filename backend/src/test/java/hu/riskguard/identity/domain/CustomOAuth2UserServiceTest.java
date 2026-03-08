package hu.riskguard.identity.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.internal.IdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private IdentityRepository identityRepository;

    private CustomOAuth2UserService customOAuth2UserService;
    private RiskGuardProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RiskGuardProperties();
        properties.getIdentity().setDefaultUserRole("SME_ADMIN");
        customOAuth2UserService = new CustomOAuth2UserService(identityRepository, properties);
    }

    @Test
    void shouldCreateNewUserAndTenantOnFirstLogin() {
        // Given
        String email = "newuser@test.com";
        String name = "New User";
        Map<String, Object> attributes = Map.of(
                "email", email,
                "name", name,
                "sub", "google-id-123"
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
        
        when(identityRepository.findUserByEmail(email)).thenReturn(Optional.empty());
        when(identityRepository.saveUser(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user;
        });

        // When
        customOAuth2UserService.processOAuth2User(userRequest, oAuth2User);

        // Then
        verify(identityRepository).saveTenant(any(Tenant.class));
        verify(identityRepository).saveUser(any(User.class));
        verify(identityRepository).saveTenantMandate(any(TenantMandate.class));
    }

    @Test
    void shouldNotCreateNewUserIfEmailAlreadyExists() {
        // Given
        String email = "existing@test.com";
        Map<String, Object> attributes = Map.of(
                "email", email,
                "sub", "google-id-123"
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
        
        when(identityRepository.findUserByEmail(email)).thenReturn(Optional.of(new User()));

        // When
        customOAuth2UserService.processOAuth2User(userRequest, oAuth2User);

        // Then
        verify(identityRepository, never()).saveTenant(any());
        verify(identityRepository, never()).saveUser(any());
    }

    @Test
    void shouldCreateNewUserAndTenantOnMicrosoftLogin() {
        // Given
        String email = "msuser@outlook.com";
        String name = "MS User";
        Map<String, Object> attributes = Map.of(
                "email", email,
                "name", name,
                "sub", "ms-id-456"
        );

        OAuth2UserRequest userRequest = mock(OAuth2UserRequest.class);
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("microsoft")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("id")
                .tokenUri("uri")
                .authorizationUri("uri")
                .redirectUri("uri")
                .build();

        when(userRequest.getClientRegistration()).thenReturn(clientRegistration);

        OAuth2User oAuth2User = new DefaultOAuth2User(Collections.emptyList(), attributes, "sub");

        when(identityRepository.findUserByEmail(email)).thenReturn(Optional.empty());
        when(identityRepository.saveUser(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user;
        });

        // When
        customOAuth2UserService.processOAuth2User(userRequest, oAuth2User);

        // Then
        verify(identityRepository).saveTenant(any(Tenant.class));
        verify(identityRepository).saveUser(any(User.class));
        verify(identityRepository).saveTenantMandate(any(TenantMandate.class));
    }

    // --- sanitizeOAuth2Name tests ---

    @Test
    void sanitizeOAuth2Name_shouldReturnNullForNullInput() {
        assertThat(CustomOAuth2UserService.sanitizeOAuth2Name(null)).isNull();
    }

    @Test
    void sanitizeOAuth2Name_shouldReturnNullForBlankInput() {
        assertThat(CustomOAuth2UserService.sanitizeOAuth2Name("   ")).isNull();
    }

    @Test
    void sanitizeOAuth2Name_shouldStripControlCharacters() {
        assertThat(CustomOAuth2UserService.sanitizeOAuth2Name("John\u0000Doe\u0007")).isEqualTo("JohnDoe");
    }

    @Test
    void sanitizeOAuth2Name_shouldTrimWhitespace() {
        assertThat(CustomOAuth2UserService.sanitizeOAuth2Name("  Jane Doe  ")).isEqualTo("Jane Doe");
    }

    @Test
    void sanitizeOAuth2Name_shouldTruncateToMaxLength() {
        String longName = "A".repeat(150);
        String result = CustomOAuth2UserService.sanitizeOAuth2Name(longName);
        assertThat(result).hasSize(100);
    }

    @Test
    void sanitizeOAuth2Name_shouldReturnNullForControlCharsOnly() {
        assertThat(CustomOAuth2UserService.sanitizeOAuth2Name("\u0000\u0001\u0002")).isNull();
    }

    @Test
    void sanitizeOAuth2Name_shouldPreserveValidUnicodeCharacters() {
        assertThat(CustomOAuth2UserService.sanitizeOAuth2Name("Kovács András")).isEqualTo("Kovács András");
    }
}