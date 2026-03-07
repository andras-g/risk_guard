package hu.riskguard.core.config;

import hu.riskguard.core.security.TenantContext;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.UUID;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("RG-Async-");
        executor.setTaskDecorator(new TenantAwareTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Propagates both MDC context and TenantContext (ThreadLocal) from the
     * parent thread to the async thread. Without this, any async operation
     * would lose tenant isolation entirely.
     */
    public static class TenantAwareTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            UUID tenantId = TenantContext.getCurrentTenant();
            return () -> {
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    if (tenantId != null) {
                        TenantContext.setCurrentTenant(tenantId);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                    TenantContext.clear();
                }
            };
        }
    }
}
