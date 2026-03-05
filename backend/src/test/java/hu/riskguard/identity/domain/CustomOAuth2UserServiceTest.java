package hu.riskguard.identity.domain;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    private CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        customOAuth2UserService = new CustomOAuth2UserService(userRepository, tenantRepository);
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
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // When
        customOAuth2UserService.processOAuth2User(userRequest, oAuth2User);

        // Then
        verify(tenantRepository).save(any(Tenant.class));
        verify(userRepository).save(any(User.class));
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
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(new User()));

        // When
        customOAuth2UserService.processOAuth2User(userRequest, oAuth2User);

        // Then
        verify(tenantRepository, never()).save(any());
        verify(userRepository, times(0)).save(any());
    }
}
