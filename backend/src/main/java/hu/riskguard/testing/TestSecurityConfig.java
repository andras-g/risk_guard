package hu.riskguard.testing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only security configuration that permits unauthenticated access to
 * {@code /api/test/**} endpoints. This filter chain has higher priority
 * ({@code @Order(1)}) than the main {@link hu.riskguard.core.config.SecurityConfig}
 * filter chain so it intercepts test paths before the main chain requires JWT auth.
 *
 * <p><strong>Security:</strong> Only active when {@code SPRING_PROFILES_ACTIVE=test}.
 */
@Configuration
@Profile({"test", "e2e"})
public class TestSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/test/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
