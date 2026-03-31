package hu.riskguard.epr;

import hu.riskguard.epr.domain.DagEngine;
import hu.riskguard.epr.domain.EprConfigValidator;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.domain.FeeCalculator;
import hu.riskguard.epr.domain.MohuExporter;
import hu.riskguard.epr.internal.EprRepository;
import hu.riskguard.epr.internal.EprRepository.TemplateCopyData;
import hu.riskguard.jooq.tables.records.EprMaterialTemplatesRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EprService} with mocked repository.
 * Tests business logic, copy logic, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class EprServiceTest {

    @Mock
    private EprRepository eprRepository;

    @Mock
    private DagEngine dagEngine;

    @Mock
    private MohuExporter mohuExporter;

    @Mock
    private EprConfigValidator eprConfigValidator;

    private EprService eprService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        eprService = new EprService(eprRepository, dagEngine, new FeeCalculator(), mohuExporter, eprConfigValidator);
    }

    @Test
    void isHealthyShouldReturnTrue() {
        assertThat(eprService.isHealthy()).isTrue();
    }

    @Test
    void createTemplateShouldDelegateToRepository() {
        UUID expectedId = UUID.randomUUID();
        // null recurring → defaults to true
        when(eprRepository.insertTemplate(eq(TENANT_ID), eq("Box"), eq(new BigDecimal("50")), eq(true)))
                .thenReturn(expectedId);

        UUID result = eprService.createTemplate(TENANT_ID, "Box", new BigDecimal("50"), null);

        assertThat(result).isEqualTo(expectedId);
        verify(eprRepository).insertTemplate(TENANT_ID, "Box", new BigDecimal("50"), true);
    }

    @Test
    void createTemplateWithRecurringTrueShouldPassToRepository() {
        UUID expectedId = UUID.randomUUID();
        when(eprRepository.insertTemplate(eq(TENANT_ID), eq("Box"), eq(new BigDecimal("10")), eq(true)))
                .thenReturn(expectedId);

        UUID result = eprService.createTemplate(TENANT_ID, "Box", new BigDecimal("10"), true);

        assertThat(result).isEqualTo(expectedId);
        verify(eprRepository).insertTemplate(TENANT_ID, "Box", new BigDecimal("10"), true);
    }

    @Test
    void createTemplateWithRecurringFalseShouldPassToRepository() {
        UUID expectedId = UUID.randomUUID();
        when(eprRepository.insertTemplate(eq(TENANT_ID), eq("OneTime"), eq(new BigDecimal("10")), eq(false)))
                .thenReturn(expectedId);

        UUID result = eprService.createTemplate(TENANT_ID, "OneTime", new BigDecimal("10"), false);

        assertThat(result).isEqualTo(expectedId);
        verify(eprRepository).insertTemplate(TENANT_ID, "OneTime", new BigDecimal("10"), false);
    }

    @Test
    void createTemplateWithNullRecurringShouldDefaultToTrue() {
        UUID expectedId = UUID.randomUUID();
        when(eprRepository.insertTemplate(eq(TENANT_ID), anyString(), any(), eq(true)))
                .thenReturn(expectedId);

        eprService.createTemplate(TENANT_ID, "Template", new BigDecimal("10"), null);

        verify(eprRepository).insertTemplate(eq(TENANT_ID), eq("Template"), any(), eq(true));
    }

    @Test
    void updateTemplateShouldReturnTrueWhenFound() {
        UUID id = UUID.randomUUID();
        // null recurring → defaults to true
        when(eprRepository.updateTemplate(id, TENANT_ID, "New", new BigDecimal("99"), true))
                .thenReturn(true);

        boolean result = eprService.updateTemplate(id, TENANT_ID, "New", new BigDecimal("99"), null);

        assertThat(result).isTrue();
    }

    @Test
    void updateTemplateShouldReturnFalseWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(eprRepository.updateTemplate(id, TENANT_ID, "New", new BigDecimal("99"), true))
                .thenReturn(false);

        boolean result = eprService.updateTemplate(id, TENANT_ID, "New", new BigDecimal("99"), null);

        assertThat(result).isFalse();
    }

    @Test
    void deleteTemplateShouldDelegateToRepository() {
        UUID id = UUID.randomUUID();
        when(eprRepository.deleteTemplate(id, TENANT_ID)).thenReturn(true);

        boolean result = eprService.deleteTemplate(id, TENANT_ID);

        assertThat(result).isTrue();
        verify(eprRepository).deleteTemplate(id, TENANT_ID);
    }

    @Test
    void toggleRecurringShouldDelegateToRepository() {
        UUID id = UUID.randomUUID();
        when(eprRepository.updateRecurring(id, TENANT_ID, false)).thenReturn(true);

        boolean result = eprService.toggleRecurring(id, TENANT_ID, false);

        assertThat(result).isTrue();
        verify(eprRepository).updateRecurring(id, TENANT_ID, false);
    }

    @Test
    void copyFromQuarterShouldCopyRecurringByDefault() {
        // recurring=true templates should be copied when includeNonRecurring=false
        EprMaterialTemplatesRecord recurring = buildRecord("Regular", new BigDecimal("10"), true);
        EprMaterialTemplatesRecord nonRecurring = buildRecord("OneTime", new BigDecimal("5"), false);
        when(eprRepository.findByTenantAndQuarter(TENANT_ID, 2025, 4))
                .thenReturn(List.of(recurring, nonRecurring));
        when(eprRepository.bulkInsertTemplates(eq(TENANT_ID), anyList()))
                .thenReturn(List.of(UUID.randomUUID()));

        List<UUID> result = eprService.copyFromQuarter(TENANT_ID, 2025, 4, false);

        assertThat(result).hasSize(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TemplateCopyData>> captor = ArgumentCaptor.forClass(List.class);
        verify(eprRepository).bulkInsertTemplates(eq(TENANT_ID), captor.capture());
        List<TemplateCopyData> copied = captor.getValue();
        assertThat(copied).hasSize(1);
        assertThat(copied.get(0).name()).isEqualTo("Regular");
    }

    @Test
    void copyFromQuarterWithIncludeNonRecurringShouldCopyAll() {
        EprMaterialTemplatesRecord recurring = buildRecord("Regular", new BigDecimal("10"), true);
        EprMaterialTemplatesRecord nonRecurring = buildRecord("OneTime", new BigDecimal("5"), false);
        when(eprRepository.findByTenantAndQuarter(TENANT_ID, 2025, 4))
                .thenReturn(List.of(recurring, nonRecurring));
        when(eprRepository.bulkInsertTemplates(eq(TENANT_ID), anyList()))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        List<UUID> result = eprService.copyFromQuarter(TENANT_ID, 2025, 4, true);

        assertThat(result).hasSize(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TemplateCopyData>> captor = ArgumentCaptor.forClass(List.class);
        verify(eprRepository).bulkInsertTemplates(eq(TENANT_ID), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void copyFromQuarterWithNoSourceTemplatesShouldReturnEmpty() {
        when(eprRepository.findByTenantAndQuarter(TENANT_ID, 2020, 1))
                .thenReturn(List.of());
        when(eprRepository.bulkInsertTemplates(eq(TENANT_ID), eq(List.of())))
                .thenReturn(List.of());

        List<UUID> result = eprService.copyFromQuarter(TENANT_ID, 2020, 1, false);

        assertThat(result).isEmpty();
    }

    @Test
    void findTemplateShouldDelegateToRepository() {
        UUID id = UUID.randomUUID();
        EprMaterialTemplatesRecord record = new EprMaterialTemplatesRecord();
        when(eprRepository.findByIdAndTenant(id, TENANT_ID)).thenReturn(Optional.of(record));

        Optional<EprMaterialTemplatesRecord> result = eprService.findTemplate(id, TENANT_ID);

        assertThat(result).isPresent();
        verify(eprRepository).findByIdAndTenant(id, TENANT_ID);
    }

    @Test
    void findTemplatesByIdsShouldDelegateToRepository() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<UUID> ids = List.of(id1, id2);
        EprMaterialTemplatesRecord r1 = buildRecord("T1", new BigDecimal("10"), true);
        EprMaterialTemplatesRecord r2 = buildRecord("T2", new BigDecimal("20"), false);
        when(eprRepository.findByIdsAndTenant(ids, TENANT_ID)).thenReturn(List.of(r1, r2));

        List<EprMaterialTemplatesRecord> result = eprService.findTemplatesByIds(ids, TENANT_ID);

        assertThat(result).hasSize(2);
        verify(eprRepository).findByIdsAndTenant(ids, TENANT_ID);
    }

    @Test
    void findTemplatesByIdsWithEmptyListShouldReturnEmpty() {
        when(eprRepository.findByIdsAndTenant(List.of(), TENANT_ID)).thenReturn(List.of());

        List<EprMaterialTemplatesRecord> result = eprService.findTemplatesByIds(List.of(), TENANT_ID);

        assertThat(result).isEmpty();
    }

    private EprMaterialTemplatesRecord buildRecord(String name, BigDecimal weight, boolean recurring) {
        EprMaterialTemplatesRecord record = new EprMaterialTemplatesRecord();
        record.setId(UUID.randomUUID());
        record.setTenantId(TENANT_ID);
        record.setName(name);
        record.setBaseWeightGrams(weight);
        record.setRecurring(recurring);
        record.setVerified(false);
        record.setCreatedAt(OffsetDateTime.now());
        record.setUpdatedAt(OffsetDateTime.now());
        return record;
    }
}
