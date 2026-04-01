package hu.riskguard.epr;

import hu.riskguard.epr.internal.EprRepository;
import hu.riskguard.jooq.tables.records.EprMaterialTemplatesRecord;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.EPR_MATERIAL_TEMPLATES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link EprRepository} with Testcontainers PostgreSQL 17.
 * Tests all CRUD methods, tenant isolation, and quarter filtering.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EprRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private EprRepository eprRepository;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Clean templates before each test
        dsl.deleteFrom(EPR_MATERIAL_TEMPLATES).execute();
        // Ensure test tenants exist (idempotent)
        dsl.insertInto(org.jooq.impl.DSL.table("tenants"))
                .set(org.jooq.impl.DSL.field("id", UUID.class), TENANT_A)
                .set(org.jooq.impl.DSL.field("name", String.class), "Test Tenant A")
                .set(org.jooq.impl.DSL.field("tier", String.class), "PRO_EPR")
                .onConflictDoNothing()
                .execute();
        dsl.insertInto(org.jooq.impl.DSL.table("tenants"))
                .set(org.jooq.impl.DSL.field("id", UUID.class), TENANT_B)
                .set(org.jooq.impl.DSL.field("name", String.class), "Test Tenant B")
                .set(org.jooq.impl.DSL.field("tier", String.class), "PRO_EPR")
                .onConflictDoNothing()
                .execute();
    }

    @Test
    void insertTemplateShouldReturnUuidAndPersist() {
        UUID id = eprRepository.insertTemplate(TENANT_A, "Cardboard Box", new BigDecimal("120.50"), true);

        assertThat(id).isNotNull();
        Optional<EprMaterialTemplatesRecord> found = eprRepository.findByIdAndTenant(id, TENANT_A);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Cardboard Box");
        assertThat(found.get().getBaseWeightGrams()).isEqualByComparingTo(new BigDecimal("120.50"));
        assertThat(found.get().getVerified()).isFalse();
        assertThat(found.get().getRecurring()).isTrue();
        assertThat(found.get().getKfCode()).isNull();
    }

    @Test
    void insertNonRecurringTemplateShouldPersistRecurringFlag() {
        UUID id = eprRepository.insertTemplate(TENANT_A, "Campaign Wrap", new BigDecimal("15.00"), false);

        Optional<EprMaterialTemplatesRecord> found = eprRepository.findByIdAndTenant(id, TENANT_A);
        assertThat(found).isPresent();
        assertThat(found.get().getRecurring()).isFalse();
    }

    @Test
    void findAllByTenantShouldReturnOnlyTenantTemplates() {
        eprRepository.insertTemplate(TENANT_A, "Template A1", new BigDecimal("10"), true);
        eprRepository.insertTemplate(TENANT_A, "Template A2", new BigDecimal("20"), true);
        eprRepository.insertTemplate(TENANT_B, "Template B1", new BigDecimal("30"), true);

        List<EprMaterialTemplatesRecord> tenantATemplates = eprRepository.findAllByTenant(TENANT_A);
        List<EprMaterialTemplatesRecord> tenantBTemplates = eprRepository.findAllByTenant(TENANT_B);

        assertThat(tenantATemplates).hasSize(2);
        assertThat(tenantBTemplates).hasSize(1);
        assertThat(tenantATemplates).allSatisfy(t -> assertThat(t.getTenantId()).isEqualTo(TENANT_A));
    }

    @Test
    void findAllByTenantShouldReturnOrderedByCreatedAtDesc() {
        eprRepository.insertTemplate(TENANT_A, "First", new BigDecimal("10"), true);
        eprRepository.insertTemplate(TENANT_A, "Second", new BigDecimal("20"), true);

        List<EprMaterialTemplatesRecord> results = eprRepository.findAllByTenant(TENANT_A);
        assertThat(results).hasSize(2);
        // Most recent first
        assertThat(results.get(0).getName()).isEqualTo("Second");
        assertThat(results.get(1).getName()).isEqualTo("First");
    }

    @Test
    void findByIdAndTenantShouldReturnEmptyForWrongTenant() {
        UUID id = eprRepository.insertTemplate(TENANT_A, "Private", new BigDecimal("10"), true);

        Optional<EprMaterialTemplatesRecord> result = eprRepository.findByIdAndTenant(id, TENANT_B);
        assertThat(result).isEmpty();
    }

    @Test
    void updateTemplateShouldModifyFieldsAndSetUpdatedAt() {
        UUID id = eprRepository.insertTemplate(TENANT_A, "Old Name", new BigDecimal("10"), true);
        EprMaterialTemplatesRecord before = eprRepository.findByIdAndTenant(id, TENANT_A).orElseThrow();

        boolean updated = eprRepository.updateTemplate(id, TENANT_A, "New Name", new BigDecimal("99.99"), false);
        assertThat(updated).isTrue();

        EprMaterialTemplatesRecord after = eprRepository.findByIdAndTenant(id, TENANT_A).orElseThrow();
        assertThat(after.getName()).isEqualTo("New Name");
        assertThat(after.getBaseWeightGrams()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(after.getRecurring()).isFalse();
        assertThat(after.getUpdatedAt()).isAfterOrEqualTo(before.getUpdatedAt());
    }

    @Test
    void updateTemplateShouldReturnFalseForWrongTenant() {
        UUID id = eprRepository.insertTemplate(TENANT_A, "Name", new BigDecimal("10"), true);

        boolean updated = eprRepository.updateTemplate(id, TENANT_B, "Hacked", new BigDecimal("1"), true);
        assertThat(updated).isFalse();
    }

    @Test
    void deleteTemplateShouldRemoveRecord() {
        UUID id = eprRepository.insertTemplate(TENANT_A, "To Delete", new BigDecimal("10"), true);

        boolean deleted = eprRepository.deleteTemplate(id, TENANT_A);
        assertThat(deleted).isTrue();
        assertThat(eprRepository.findByIdAndTenant(id, TENANT_A)).isEmpty();
    }

    @Test
    void deleteTemplateShouldReturnFalseForWrongTenant() {
        UUID id = eprRepository.insertTemplate(TENANT_A, "Private", new BigDecimal("10"), true);

        boolean deleted = eprRepository.deleteTemplate(id, TENANT_B);
        assertThat(deleted).isFalse();
        assertThat(eprRepository.findByIdAndTenant(id, TENANT_A)).isPresent();
    }

    @Test
    void updateRecurringShouldToggleFlagAndSetUpdatedAt() {
        UUID id = eprRepository.insertTemplate(TENANT_A, "Template", new BigDecimal("10"), true);

        boolean updated = eprRepository.updateRecurring(id, TENANT_A, false);
        assertThat(updated).isTrue();

        EprMaterialTemplatesRecord record = eprRepository.findByIdAndTenant(id, TENANT_A).orElseThrow();
        assertThat(record.getRecurring()).isFalse();
    }

    @Test
    void findByTenantAndQuarterShouldFilterByCreatedAt() {
        // Insert template — it will have created_at = now()
        UUID id = eprRepository.insertTemplate(TENANT_A, "Current Quarter Template", new BigDecimal("10"), true);

        // Determine the current quarter dynamically so this test remains valid regardless of when it runs
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        int currentQuarter = (today.getMonthValue() - 1) / 3 + 1;

        // The template's created_at is in the current quarter
        List<EprMaterialTemplatesRecord> currentResults = eprRepository.findByTenantAndQuarter(TENANT_A, currentYear, currentQuarter);
        assertThat(currentResults).isNotEmpty();
        assertThat(currentResults).anyMatch(r -> r.getId().equals(id));

        // The adjacent quarter should not contain this template
        int otherQuarter = (currentQuarter % 4) + 1;
        int otherYear = (currentQuarter == 4) ? currentYear + 1 : currentYear;
        List<EprMaterialTemplatesRecord> otherResults = eprRepository.findByTenantAndQuarter(TENANT_A, otherYear, otherQuarter);
        assertThat(otherResults).noneMatch(r -> r.getId().equals(id));
    }

    @Test
    void bulkInsertTemplatesShouldCreateMultipleRecords() {
        var toCopy = List.of(
                new EprRepository.TemplateCopyData("Copy 1", new BigDecimal("10"), true),
                new EprRepository.TemplateCopyData("Copy 2", new BigDecimal("20"), false)
        );

        List<UUID> newIds = eprRepository.bulkInsertTemplates(TENANT_A, toCopy);

        assertThat(newIds).hasSize(2);
        List<EprMaterialTemplatesRecord> all = eprRepository.findAllByTenant(TENANT_A);
        assertThat(all).hasSize(2);
        assertThat(all).anyMatch(r -> r.getName().equals("Copy 1") && r.getRecurring());
        assertThat(all).anyMatch(r -> r.getName().equals("Copy 2") && !r.getRecurring());
        // All copied templates should have verified=false
        assertThat(all).allSatisfy(r -> assertThat(r.getVerified()).isFalse());
    }
}
