package hu.riskguard.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.mockito.Mockito.mock;

@Configuration
@Profile("test")
public class SecurityTestConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return mock(ClientRegistrationRepository.class);
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return mock(JwtDecoder.class);
    }
}
