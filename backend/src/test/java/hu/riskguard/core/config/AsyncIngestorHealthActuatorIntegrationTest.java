package hu.riskguard.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying that {@code GET /actuator/health} includes the
 * {@code asyncIngestor} health component with the expected structure.
 *
 * <p>AC6 requires the health endpoint to report ingestor status. This test
 * validates the Spring Boot auto-wiring of {@link AsyncIngestorHealthIndicator}
 * and its component naming convention (see review finding [MEDIUM]:
 * "No integration test validates asyncIngestor component naming").
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers
class AsyncIngestorHealthActuatorIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private AsyncIngestorHealthState healthState;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
        // Reset shared health state between tests — it's a singleton Spring bean
        healthState.reset();
    }

    @Test
    void actuatorHealth_containsAsyncIngestorComponent_neverRun() throws Exception {
        // Given — ingestor has never run (default state)

        // When/Then — asyncIngestor component exists with expected structure
        // Note: overall health may return 503 if other components are DOWN (e.g., circuit breakers
        // in half-open state during test). We don't assert the HTTP status — we only care that
        // the asyncIngestor component is present and correctly structured (AC6 naming validation).
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.asyncIngestor").exists())
                .andExpect(jsonPath("$.components.asyncIngestor.status").value("UP"))
                .andExpect(jsonPath("$.components.asyncIngestor.details.lastRun").value("never"))
                .andExpect(jsonPath("$.components.asyncIngestor.details.lastEntriesProcessed").value(0))
                .andExpect(jsonPath("$.components.asyncIngestor.details.lastErrorCount").value(0));
    }

    @Test
    void actuatorHealth_containsAsyncIngestorComponent_afterRun() throws Exception {
        // Given — simulate a completed ingestor run
        healthState.recordRun(5, 1);

        // When/Then — asyncIngestor component reflects the run data
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.asyncIngestor").exists())
                .andExpect(jsonPath("$.components.asyncIngestor.status").value("UP"))
                .andExpect(jsonPath("$.components.asyncIngestor.details.lastRun").isNotEmpty())
                .andExpect(jsonPath("$.components.asyncIngestor.details.lastEntriesProcessed").value(5))
                .andExpect(jsonPath("$.components.asyncIngestor.details.lastErrorCount").value(1));
    }
}
