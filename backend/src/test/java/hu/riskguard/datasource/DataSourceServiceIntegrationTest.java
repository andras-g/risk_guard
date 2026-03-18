package hu.riskguard.datasource;

import hu.riskguard.core.security.TenantContext;
import hu.riskguard.datasource.api.dto.CompanyData;
import hu.riskguard.datasource.domain.DataSourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link DataSourceService} in demo mode.
 * Verifies the full data retrieval flow: DataSourceService → CompanyDataAggregator → DemoCompanyDataAdapter.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DataSourceServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private DataSourceService dataSourceService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("fetchCompanyData returns valid CompanyData with demo adapter results")
    void fetchCompanyDataReturnsValidResult() {
        CompanyData result = dataSourceService.fetchCompanyData("12345678");

        assertThat(result).isNotNull();
        assertThat(result.snapshotData()).isNotEmpty();
        assertThat(result.adapterResults()).containsKey("demo");
        assertThat(result.adapterResults().get("demo").available()).isTrue();
        assertThat(result.adapterResults().get("demo").adapterName()).isEqualTo("demo");
    }

    @Test
    @DisplayName("fetchCompanyData includes demo adapter data in snapshot")
    void fetchCompanyDataIncludesDemoData() {
        CompanyData result = dataSourceService.fetchCompanyData("11223344");

        // Adós Szolgáltató Bt. — has public debt
        @SuppressWarnings("unchecked")
        var demoData = (java.util.Map<String, Object>) result.snapshotData().get("demo");
        assertThat(demoData).containsEntry("hasPublicDebt", true);
        assertThat(demoData).containsEntry("companyName", "Adós Szolgáltató Bt.");
    }

    @Test
    @DisplayName("fetchCompanyData produces source URLs")
    void fetchCompanyDataProducesSourceUrls() {
        CompanyData result = dataSourceService.fetchCompanyData("12345678");

        assertThat(result.sourceUrls()).isNotEmpty();
        assertThat(result.sourceUrls().getFirst()).startsWith("demo://");
    }

    @Test
    @DisplayName("fetchCompanyData produces dom fingerprint hash")
    void fetchCompanyDataProducesHash() {
        CompanyData result = dataSourceService.fetchCompanyData("12345678");

        assertThat(result.domFingerprintHash()).isNotNull();
        assertThat(result.domFingerprintHash()).hasSize(64); // SHA-256 hex
    }

    @Test
    @DisplayName("unknown tax number still returns valid result in demo mode")
    void unknownTaxNumberReturnsValidResult() {
        CompanyData result = dataSourceService.fetchCompanyData("99999999");

        assertThat(result.adapterResults().get("demo").available()).isTrue();
        @SuppressWarnings("unchecked")
        var demoData = (java.util.Map<String, Object>) result.snapshotData().get("demo");
        assertThat(demoData).containsEntry("companyName", "Ismeretlen Cég Kft.");
    }
}
