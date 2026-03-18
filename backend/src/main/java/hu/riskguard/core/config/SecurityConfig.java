package hu.riskguard.core.config;

import hu.riskguard.core.security.HttpCookieOAuth2AuthorizationRequestRepository;
import hu.riskguard.core.security.OAuth2AuthenticationFailureHandler;
import hu.riskguard.core.security.OAuth2AuthenticationSuccessHandler;
import hu.riskguard.core.security.TenantFilter;
import hu.riskguard.core.security.TokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TenantFilter tenantFilter;
    private final TokenProvider tokenProvider;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;
    private final OAuth2AuthenticationFailureHandler oauth2FailureHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;
    private final RiskGuardProperties properties;

    // Public paths that must never be challenged by the resource server entry point.
    // NOTE: /actuator/ is intentionally EXCLUDED so that the bearer-token resolver can
    // extract JWT cookies on actuator paths. This allows `show-details: when-authorized`
    // in production to work correctly — authenticated operators see health component details
    // while unauthenticated probes (Cloud Run liveness/readiness) get the basic status.
    // The /actuator/** paths remain permitAll() in authorizeHttpRequests so unauthenticated
    // requests are never blocked.
    private static final String[] PUBLIC_PATH_PREFIXES = {
        "/oauth2/", "/login/", "/api/public/", "/v3/api-docs", "/swagger-ui", "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // CSRF is disabled for the API because authentication uses HttpOnly SameSite=Lax
            // cookies — browsers won't send these cookies on cross-site requests, making CSRF
            // attacks impossible. Re-enable if cookie SameSite policy changes.
            .csrf(csrf -> csrf.disable())
            // Use STATELESS for the API but allow the OAuth2 login flow to use a session
            // temporarily for the state/nonce parameters (Spring Security requires this).
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/public/**", "/actuator/**", "/v3/api-docs/**",
                    "/swagger-ui/**", "/login/**", "/oauth2/**", "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authorization -> authorization
                    .baseUri("/oauth2/authorization")
                    .authorizationRequestRepository(cookieAuthorizationRequestRepository)
                )
                .redirectionEndpoint(redirection -> redirection
                    .baseUri("/login/oauth2/code/*")
                )
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(oauth2SuccessHandler)
                .failureHandler(oauth2FailureHandler)
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(cookieBearerTokenResolver())
                .jwt(jwt -> jwt.decoder(jwtDecoder()))
                // Custom entry point: only challenge with 401 on protected paths.
                // On public/OAuth2 paths, pass through with a plain 401 (no WWW-Authenticate
                // Bearer header) so the browser continues the OAuth2 redirect flow.
                .authenticationEntryPoint(selectiveAuthenticationEntryPoint())
            );

        // TenantFilter MUST run after BearerTokenAuthenticationFilter, not after
        // UsernamePasswordAuthenticationFilter. BearerTokenAuthenticationFilter is the
        // filter that decodes the JWT cookie and populates SecurityContextHolder. If
        // TenantFilter runs before it, authentication is null and TenantContext is never set.
        http.addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Authentication entry point that only issues a Bearer challenge on API paths.
     * For OAuth2 initiation and login callback paths it returns 401 without a
     * WWW-Authenticate header, which tells the browser to follow redirects normally.
     *
     * In practice these paths are all covered by permitAll() so this entry point
     * should never be invoked for them — but the guard is needed because Spring
     * Security 7's resource server registers this entry point globally before
     * the authorization rules are evaluated.
     */
    private AuthenticationEntryPoint selectiveAuthenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) -> {
            String path = request.getRequestURI();
            boolean isPublicPath = false;
            for (String prefix : PUBLIC_PATH_PREFIXES) {
                if (path.startsWith(prefix)) {
                    isPublicPath = true;
                    break;
                }
            }
            if (isPublicPath) {
                // Don't challenge — let the request pass through to permitAll handlers.
                // Spring Security will continue filter chain processing.
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
            } else {
                // Standard Bearer challenge for protected API endpoints.
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setHeader("WWW-Authenticate", "Bearer");
            }
        };
    }

    private BearerTokenResolver cookieBearerTokenResolver() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        return request -> {
            // Never extract a token for OAuth2/login/public paths — prevents the
            // BearerTokenAuthenticationFilter from attempting to authenticate these requests.
            String path = request.getRequestURI();
            for (String prefix : PUBLIC_PATH_PREFIXES) {
                if (path.startsWith(prefix)) return null;
            }

            String token = resolver.resolve(request);
            if (token != null) return token;

            if (request.getCookies() != null) {
                for (var cookie : request.getCookies()) {
                    if (properties.getIdentity().getCookieName().equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
            return null;
        };
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Use TokenProvider's cached signing key — single source of truth for both signing and verification.
        // Must specify HS512 to match the algorithm selected by jjwt's Keys.hmacShaKeyFor() in TokenProvider.
        // NimbusJwtDecoder defaults to HS256 which causes silent 401s on token verification.
        return NimbusJwtDecoder.withSecretKey(tokenProvider.getSigningKey())
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }

    /**
     * CORS configuration — allows the frontend origin to call the backend API.
     * Uses {@code frontendBaseUrl} from risk-guard properties so staging/production
     * automatically pick up the correct origin via env vars.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.getSecurity().getFrontendBaseUrl()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // Required for HttpOnly cookie auth
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
