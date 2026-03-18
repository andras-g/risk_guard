package hu.riskguard.core.config;

import hu.riskguard.core.security.TierGateInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link TierGateInterceptor} for all authenticated API paths.
 * Public paths (/api/public/**) are excluded — they are not tier-gated.
 */
@Configuration
@RequiredArgsConstructor
public class TierGateConfig implements WebMvcConfigurer {

    private final TierGateInterceptor tierGateInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tierGateInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/public/**");
    }
}
