package hu.riskguard.datasource.internal;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Listens to Resilience4j circuit breaker events and persists health metrics to
 * {@code adapter_health} table via {@link AdapterHealthRepository}.
 *
 * <p>Registered on all circuit breakers found in the registry at startup via {@code @PostConstruct}.
 * Tracks success/error counts and MTBF per adapter.
 */
@Component
public class CircuitBreakerEventListener {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerEventListener.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final AdapterHealthRepository adapterHealthRepository;

    public CircuitBreakerEventListener(CircuitBreakerRegistry circuitBreakerRegistry,
                                       AdapterHealthRepository adapterHealthRepository) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.adapterHealthRepository = adapterHealthRepository;
    }

    @PostConstruct
    public void init() {
        var breakers = circuitBreakerRegistry.getAllCircuitBreakers();
        breakers.forEach(this::registerListeners);
        log.info("CircuitBreakerEventListener registered on {} circuit breaker(s)", breakers.size());
    }

    private void registerListeners(CircuitBreaker cb) {
        String name = cb.getName();

        cb.getEventPublisher().onSuccess(event ->
                adapterHealthRepository.recordSuccess(name, Instant.now()));

        cb.getEventPublisher().onError(event ->
                adapterHealthRepository.recordFailure(name, Instant.now()));

        cb.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.State toState = event.getStateTransition().getToState();
            String status = switch (toState) {
                case OPEN -> "CIRCUIT_OPEN";
                case CLOSED -> "HEALTHY";
                case HALF_OPEN -> "DEGRADED";
                default -> "DEGRADED";
            };
            log.info("Circuit breaker '{}' transitioned to {} — updating adapter_health", name, toState);
            adapterHealthRepository.recordStateTransition(name, status, Instant.now());
        });
    }
}
