package hu.riskguard.identity.domain;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brute-force protection service using Caffeine cache.
 * Tracks failed login attempts per email address.
 * After 5 failures within 15 minutes, the account is temporarily locked.
 *
 * <p>The cache is injected via constructor to allow Spring-managed cache configuration,
 * actuator observability (/actuator/caches), and testability via injection.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;

    private final Cache<String, AtomicInteger> attemptsCache;

    /**
     * Default constructor — creates a standalone Caffeine cache with 15-minute TTL.
     * Used by Spring auto-wiring when no external cache is provided.
     */
    public LoginAttemptService() {
        this(Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build());
    }

    /**
     * Constructor accepting an external cache instance — enables Spring CacheManager integration,
     * actuator visibility, and injection of test-controllable caches.
     */
    public LoginAttemptService(Cache<String, AtomicInteger> attemptsCache) {
        this.attemptsCache = attemptsCache;
    }

    /**
     * Record a failed login attempt for the given email.
     */
    public void recordFailedAttempt(String email) {
        attemptsCache.asMap()
                .computeIfAbsent(email.toLowerCase(), k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * Check if the given email is currently locked out due to too many failed attempts.
     */
    public boolean isLockedOut(String email) {
        AtomicInteger attempts = attemptsCache.getIfPresent(email.toLowerCase());
        return attempts != null && attempts.get() >= MAX_ATTEMPTS;
    }

    /**
     * Reset the failed attempt counter on successful login.
     */
    public void resetAttempts(String email) {
        attemptsCache.invalidate(email.toLowerCase());
    }
}
