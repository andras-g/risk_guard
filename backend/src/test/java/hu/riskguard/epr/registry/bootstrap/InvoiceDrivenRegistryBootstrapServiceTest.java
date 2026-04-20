package hu.riskguard.epr.registry.bootstrap;

import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDetail;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import hu.riskguard.epr.registry.api.dto.BatchPackagingResult;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobRecord;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobStatus;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapPreconditionException;
import hu.riskguard.epr.registry.bootstrap.domain.InvoiceDrivenRegistryBootstrapService;
import hu.riskguard.epr.registry.bootstrap.internal.BootstrapJobRepository;
import hu.riskguard.epr.registry.domain.BatchPackagingClassifierService;
import hu.riskguard.epr.registry.domain.ClassifierUsageService;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for {@link InvoiceDrivenRegistryBootstrapService#startJob} — synchronous preamble
 * (AC #32 cases a, b, c, d, e, f, g, h, i, j).
 *
 * <p>The async worker ({@link InvoiceDrivenRegistryBootstrapService#processJob}) is tested
 * via {@code processJob_emptyNavResult} and {@code processJob_capExceeded} which call the
 * method directly on a real instance with mocked collaborators and a synchronous
 * {@link TransactionTemplate} stub.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceDrivenRegistryBootstrapServiceTest {

    @Mock DataSourceService dataSourceService;
    @Mock ProducerProfileService producerProfileService;
    @Mock BatchPackagingClassifierService classifierService;
    @Mock ClassifierUsageService usageService;
    @Mock BootstrapJobRepository bootstrapJobRepository;
    @Mock RegistryRepository registryRepository;
    @Mock AuditService auditService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) DSLContext dsl;
    @Mock PlatformTransactionManager transactionManager;

    private InvoiceDrivenRegistryBootstrapService service;
    private InvoiceDrivenRegistryBootstrapService selfSpy;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID JOB_ID    = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        // Real TransactionTemplate backed by a no-op manager (synchronous — no actual DB tx)
        TransactionTemplate syncTx = new TransactionTemplate(new NoOpTransactionManager());

        service = new InvoiceDrivenRegistryBootstrapService(
                dataSourceService, producerProfileService, classifierService, usageService,
                bootstrapJobRepository, registryRepository, auditService, dsl, transactionManager);

        // Inject a spy as self so @Async processJob can be stubbed per-test
        selfSpy = spy(service);
        setField(service, "self", selfSpy);
        setField(service, "requiresNewTx", syncTx);

        // transitionStatus now returns the rows-affected count (1 when a real non-terminal
        // row was advanced). Default Mockito returns 0, which the worker interprets as
        // "already cancelled at start" and exits — break all async tests. Stub to 1 by default.
        lenient().when(bootstrapJobRepository.transitionStatus(any(), any(), any())).thenReturn(1);
    }

    // ── AC #32a: happy path ───────────────────────────────────────────────────

    @Test
    void startJob_happyPath_returnsJobId() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID))
                .thenReturn(Optional.of("12345678-1-11"));
        when(bootstrapJobRepository.findInflightByTenant(TENANT_ID))
                .thenReturn(Optional.empty());
        when(bootstrapJobRepository.insertIfNoInflight(eq(TENANT_ID), any(), any(), eq(USER_ID)))
                .thenReturn(Optional.of(JOB_ID));
        lenient().doNothing().when(selfSpy).processJob(any(), any(), any(), any(), any(), any());

        UUID result = service.startJob(TENANT_ID, USER_ID, null, null);

        assertThat(result).isEqualTo(JOB_ID);
        verify(selfSpy).processJob(eq(JOB_ID), eq(TENANT_ID), eq(USER_ID), any(), any(), eq("12345678-1-11"));
    }

    // ── AC #32a: default period (null inputs → last 3 complete months) ────────

    @Test
    void startJob_nullPeriod_defaultsToLast3Months() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID))
                .thenReturn(Optional.of("12345678-1-11"));
        when(bootstrapJobRepository.findInflightByTenant(TENANT_ID))
                .thenReturn(Optional.empty());
        when(bootstrapJobRepository.insertIfNoInflight(eq(TENANT_ID), any(), any(), any()))
                .thenReturn(Optional.of(JOB_ID));
        lenient().doNothing().when(selfSpy).processJob(any(), any(), any(), any(), any(), any());

        service.startJob(TENANT_ID, USER_ID, null, null);

        // Capture the period that was passed to insertIfNoInflight
        var captor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(bootstrapJobRepository).insertIfNoInflight(any(), captor.capture(), captor.capture(), any());
        LocalDate from = captor.getAllValues().get(0);
        LocalDate to   = captor.getAllValues().get(1);

        assertThat(from.getDayOfMonth()).isEqualTo(1); // first of month
        assertThat(to.getDayOfMonth()).isEqualTo(to.lengthOfMonth()); // last of month
        assertThat(from).isBefore(to);
        assertThat(to).isBefore(LocalDate.now()); // period is in the past
    }

    // ── AC #32a: explicit period is respected ─────────────────────────────────

    @Test
    void startJob_explicitPeriod_respected() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID))
                .thenReturn(Optional.of("12345678-1-11"));
        when(bootstrapJobRepository.findInflightByTenant(TENANT_ID))
                .thenReturn(Optional.empty());
        when(bootstrapJobRepository.insertIfNoInflight(eq(TENANT_ID), any(), any(), any()))
                .thenReturn(Optional.of(JOB_ID));
        lenient().doNothing().when(selfSpy).processJob(any(), any(), any(), any(), any(), any());

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to   = LocalDate.of(2026, 3, 31);
        service.startJob(TENANT_ID, USER_ID, from, to);

        verify(bootstrapJobRepository).insertIfNoInflight(any(), eq(from), eq(to), any());
    }

    // ── AC #32c: precondition — missing NAV credentials → 412 w/ structured code ─

    @Test
    void startJob_precondition_missingNavCredentials_returns412() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startJob(TENANT_ID, USER_ID, null, null))
                .isInstanceOf(BootstrapPreconditionException.class)
                .satisfies(e -> {
                    var ex = (BootstrapPreconditionException) e;
                    assertThat(ex.status()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
                    assertThat(ex.code()).isEqualTo("NAV_CREDENTIALS_MISSING");
                });
    }

    // ── AC #11: stored tax-number shorter than 8 chars → 403 w/ structured code ─
    // Defence-in-depth: the stored tax number must yield a valid 8-digit prefix, else
    // the invoice pull is blocked with 403.

    @Test
    void startJob_precondition_taxNumberMismatch_returns403() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID))
                .thenReturn(Optional.of("1234"));

        assertThatThrownBy(() -> service.startJob(TENANT_ID, USER_ID, null, null))
                .isInstanceOf(BootstrapPreconditionException.class)
                .satisfies(e -> {
                    var ex = (BootstrapPreconditionException) e;
                    assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.code()).isEqualTo("TAX_NUMBER_MISMATCH");
                });
    }

    // ── AC #32c: producer profile incomplete → 412 w/ structured code ─────────

    @Test
    void startJob_precondition_producerProfileIncomplete_returns412() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID))
                .thenReturn(Optional.of("12345678-1-11"));
        doThrow(new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Producer profile incomplete"))
                .when(producerProfileService).get(TENANT_ID);

        assertThatThrownBy(() -> service.startJob(TENANT_ID, USER_ID, null, null))
                .isInstanceOf(BootstrapPreconditionException.class)
                .satisfies(e -> {
                    var ex = (BootstrapPreconditionException) e;
                    assertThat(ex.status()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
                    assertThat(ex.code()).isEqualTo("PRODUCER_PROFILE_INCOMPLETE");
                });
    }

    // ── AC #32d: in-flight guard → 409 w/ ALREADY_RUNNING body carrying jobId ──

    @Test
    void startJob_inFlightGuard_returns409() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID))
                .thenReturn(Optional.of("12345678-1-11"));
        BootstrapJobRecord inflight = mockJobRecord(JOB_ID, BootstrapJobStatus.RUNNING);
        when(bootstrapJobRepository.findInflightByTenant(TENANT_ID))
                .thenReturn(Optional.of(inflight));

        assertThatThrownBy(() -> service.startJob(TENANT_ID, USER_ID, null, null))
                .isInstanceOf(BootstrapPreconditionException.class)
                .satisfies(e -> {
                    var ex = (BootstrapPreconditionException) e;
                    assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.code()).isEqualTo("ALREADY_RUNNING");
                    assertThat(ex.extraProperties()).containsEntry("jobId", JOB_ID);
                });
    }

    // ── AC #32d: race-condition insert returns empty → 409 ────────────────────

    @Test
    void startJob_raceConditionInsert_returns409() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID))
                .thenReturn(Optional.of("12345678-1-11"));
        when(bootstrapJobRepository.findInflightByTenant(TENANT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mockJobRecord(JOB_ID, BootstrapJobStatus.PENDING)));
        when(bootstrapJobRepository.insertIfNoInflight(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startJob(TENANT_ID, USER_ID, null, null))
                .isInstanceOf(BootstrapPreconditionException.class)
                .satisfies(e -> {
                    var ex = (BootstrapPreconditionException) e;
                    assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.code()).isEqualTo("ALREADY_RUNNING");
                });
    }

    // ── AC #32j: empty NAV result → COMPLETED with zero counters ─────────────

    @Test
    void processJob_emptyNavResult_completesWithZeroCounters() {
        InvoiceQueryResult emptyResult = new InvoiceQueryResult(List.of(), true);
        when(dataSourceService.queryInvoices(any(), any(), any(), eq(InvoiceDirection.OUTBOUND)))
                .thenReturn(emptyResult);

        service.processJob(JOB_ID, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "12345678-1-11");

        verify(bootstrapJobRepository).transitionStatus(JOB_ID, BootstrapJobStatus.RUNNING, null);
        verify(bootstrapJobRepository).setTotalPairs(JOB_ID, 0);
        verify(bootstrapJobRepository).transitionStatus(JOB_ID, BootstrapJobStatus.COMPLETED, null);
    }

    // ── AC #32i: cap exceeded → FAILED ────────────────────────────────────────

    @Test
    void processJob_capExceeded_failsJob() {
        // 3 unique pairs, but cap has only 1 slot left
        var line  = new InvoiceLineItem(1, "Widget A", null, null, null, null, null, "3923", null, null);
        var line2 = new InvoiceLineItem(2, "Widget B", null, null, null, null, null, "3924", null, null);
        var line3 = new InvoiceLineItem(3, "Widget C", null, null, null, null, null, "3925", null, null);
        var detail = new InvoiceDetail("INV-001", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND,
                List.of(line, line2, line3), null, null);
        var summary = new InvoiceSummary("INV-001", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND);
        var queryResult = new InvoiceQueryResult(List.of(summary), true);

        when(dataSourceService.queryInvoices(any(), any(), any(), any())).thenReturn(queryResult);
        when(dataSourceService.queryInvoiceDetails("INV-001")).thenReturn(detail);
        when(usageService.getMonthlyCap()).thenReturn(10);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(9); // 1 slot left, need 3

        service.processJob(JOB_ID, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "12345678-1-11");

        verify(bootstrapJobRepository).transitionStatus(eq(JOB_ID), eq(BootstrapJobStatus.FAILED), any());
        verifyNoInteractions(classifierService);
    }

    // ── AC #32j: NAV service unavailable → FAILED ────────────────────────────

    @Test
    void processJob_navServiceUnavailable_failsJob() {
        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(), false));

        service.processJob(JOB_ID, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "12345678-1-11");

        verify(bootstrapJobRepository).transitionStatus(eq(JOB_ID), eq(BootstrapJobStatus.FAILED), any());
    }

    // ── AC #18 + #32b: dedup key = vtsz || '~' || LOWER(TRIM(description)) ────
    //
    // Dedup collapses casing and strips leading/trailing whitespace (incl. tabs).
    // Five invoice lines all normalize to the single dedup key "3923~kávé 250g".
    // Internal multi-space is intentionally NOT collapsed (see D6 deferred note);
    // a variant with internal multi-spaces is covered in the "distinct keys" test.
    // The worker short-circuits via the cap check after setTotalPairs, so we can
    // assert the deduped count without exercising the classifier/persist path.

    @Test
    void processJob_dedupCollapses_whitespaceCasingAndTrim() {
        String vtsz = "3923";
        var line1 = new InvoiceLineItem(1, "Kávé 250g",        null, null, null, null, null, vtsz, null, null);
        var line2 = new InvoiceLineItem(2, "KÁVÉ 250G",        null, null, null, null, null, vtsz, null, null);
        var line3 = new InvoiceLineItem(3, "  Kávé 250g  ",    null, null, null, null, null, vtsz, null, null);
        var line4 = new InvoiceLineItem(4, "kávé 250g",        null, null, null, null, null, vtsz, null, null);
        var line5 = new InvoiceLineItem(5, "kávé 250g\t",      null, null, null, null, null, vtsz, null, null);

        var detail = new InvoiceDetail("INV-dedup", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND,
                List.of(line1, line2, line3, line4, line5), null, null);
        var summary = new InvoiceSummary("INV-dedup", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND);

        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-dedup")).thenReturn(detail);
        when(usageService.getMonthlyCap()).thenReturn(0);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0);

        service.processJob(JOB_ID, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "12345678");

        verify(bootstrapJobRepository).setTotalPairs(JOB_ID, 1);
    }

    // ── AC #18 + #32b: internal multi-space variants stay distinct (documents current behavior) ─

    @Test
    void processJob_dedupPreservesInternalWhitespace() {
        String vtsz = "3923";
        var line1 = new InvoiceLineItem(1, "kávé 250g",       null, null, null, null, null, vtsz, null, null);
        var line2 = new InvoiceLineItem(2, "KÁVÉ   250g",     null, null, null, null, null, vtsz, null, null); // multi-space
        var line3 = new InvoiceLineItem(3, "kávé\t250g",      null, null, null, null, null, vtsz, null, null); // internal tab

        var detail = new InvoiceDetail("INV-ws", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND,
                List.of(line1, line2, line3), null, null);
        var summary = new InvoiceSummary("INV-ws", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND);

        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-ws")).thenReturn(detail);
        when(usageService.getMonthlyCap()).thenReturn(0);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0);

        service.processJob(JOB_ID, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "12345678");

        // Three distinct keys — internal whitespace is NOT normalized.
        verify(bootstrapJobRepository).setTotalPairs(JOB_ID, 3);
    }

    // ── AC #18 + #32b: dedup keeps distinct pairs when description differs ────

    @Test
    void processJob_dedupKeepsDistinctDescriptions() {
        String vtsz = "3923";
        // Three distinct descriptions → three distinct dedup keys.
        var line1 = new InvoiceLineItem(1, "Kávé 250g",        null, null, null, null, null, vtsz, null, null);
        var line2 = new InvoiceLineItem(2, "Kávé 500g",        null, null, null, null, null, vtsz, null, null);
        var line3 = new InvoiceLineItem(3, "Tea 100g",         null, null, null, null, null, vtsz, null, null);
        // Duplicate of line1 with different casing — should collapse.
        var line4 = new InvoiceLineItem(4, "KÁVÉ 250G",        null, null, null, null, null, vtsz, null, null);

        var detail = new InvoiceDetail("INV-distinct", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND,
                List.of(line1, line2, line3, line4), null, null);
        var summary = new InvoiceSummary("INV-distinct", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND);

        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-distinct")).thenReturn(detail);
        when(usageService.getMonthlyCap()).thenReturn(0);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0);

        service.processJob(JOB_ID, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "12345678");

        // 4 raw lines collapsed to 3 distinct pairs (Kávé 250g, Kávé 500g, Tea 100g).
        verify(bootstrapJobRepository).setTotalPairs(JOB_ID, 3);
    }

    // ── AC #18: null/blank vtsz and null/blank description are skipped ────────

    @Test
    void processJob_dedupSkipsNullOrBlankLines() {
        var lineOk       = new InvoiceLineItem(1, "Widget",   null, null, null, null, null, "3923", null, null);
        var lineNullVtsz = new InvoiceLineItem(2, "Other",    null, null, null, null, null, null,   null, null);
        var lineBlankVtsz = new InvoiceLineItem(3, "Other",   null, null, null, null, null, "   ",  null, null);
        var lineNullDesc = new InvoiceLineItem(4, null,       null, null, null, null, null, "3924", null, null);
        var lineBlankDesc = new InvoiceLineItem(5, "   ",     null, null, null, null, null, "3925", null, null);

        var detail = new InvoiceDetail("INV-null", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND,
                List.of(lineOk, lineNullVtsz, lineBlankVtsz, lineNullDesc, lineBlankDesc), null, null);
        var summary = new InvoiceSummary("INV-null", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND);

        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-null")).thenReturn(detail);
        when(usageService.getMonthlyCap()).thenReturn(0);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0);

        service.processJob(JOB_ID, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "12345678");

        // Only one valid line survived → total_pairs = 1.
        verify(bootstrapJobRepository).setTotalPairs(JOB_ID, 1);
    }

    // ── AC #32 / P6: classifier returning fewer results than pairs → FAILED ──

    @Test
    void processJob_classifierSizeMismatch_failsJobWithoutPersisting() {
        String vtsz = "3923";
        var line1 = new InvoiceLineItem(1, "A", null, null, null, null, null, "3923", null, null);
        var line2 = new InvoiceLineItem(2, "B", null, null, null, null, null, "3924", null, null);
        var line3 = new InvoiceLineItem(3, "C", null, null, null, null, null, "3925", null, null);

        var detail = new InvoiceDetail("INV-mis", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND,
                List.of(line1, line2, line3), null, null);
        var summary = new InvoiceSummary("INV-mis", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND);

        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-mis")).thenReturn(detail);
        when(usageService.getMonthlyCap()).thenReturn(1000);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0);
        // readStatus is invoked at the top of each chunk; return RUNNING so the
        // worker doesn't interpret Optional.empty() as "cancelled" and exit early.
        when(bootstrapJobRepository.readStatus(JOB_ID))
                .thenReturn(Optional.of(BootstrapJobStatus.RUNNING));
        // Classifier returns only 2 results for 3 pairs — misalignment would write
        // pair C's data against row B's position if not caught.
        BatchPackagingResult r1 = new BatchPackagingResult("3923", "A",
                List.of(), BatchPackagingResult.STRATEGY_UNRESOLVED, "v1");
        BatchPackagingResult r2 = new BatchPackagingResult("3924", "B",
                List.of(), BatchPackagingResult.STRATEGY_UNRESOLVED, "v1");
        when(classifierService.classify(any(), eq(TENANT_ID))).thenReturn(List.of(r1, r2));

        service.processJob(JOB_ID, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "12345678");

        verify(bootstrapJobRepository).transitionStatus(eq(JOB_ID), eq(BootstrapJobStatus.FAILED), any());
        // No per-pair persistence should have happened.
        verifyNoInteractions(registryRepository);
    }

    // ── AC #32c/d/e/f: overwrite, unrelated-row isolation, UNRESOLVED tagging,
    // VTSZ_FALLBACK tagging — all require the persistPair DB round-trip, which is
    // covered end-to-end by InvoiceDrivenRegistryBootstrapIntegrationTest against
    // Testcontainers Postgres. Unit-scope check below verifies that the worker
    // correctly dispatches distinct pairs to the classifier, which is the only
    // behavior observable without a real DB.

    @Test
    void processJob_classifierDispatch_sendsDistinctPairs() {
        var line1 = new InvoiceLineItem(1, "A", null, null, null, null, null, "3923", null, null);
        var line2 = new InvoiceLineItem(2, "B", null, null, null, null, null, "3924", null, null);
        var line3 = new InvoiceLineItem(3, "A", null, null, null, null, null, "3923", null, null); // dup of line1

        var detail = new InvoiceDetail("INV-dispatch", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND,
                List.of(line1, line2, line3), null, null);
        var summary = new InvoiceSummary("INV-dispatch", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND);

        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-dispatch")).thenReturn(detail);
        when(usageService.getMonthlyCap()).thenReturn(1000);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0);
        when(bootstrapJobRepository.readStatus(JOB_ID))
                .thenReturn(Optional.of(BootstrapJobStatus.RUNNING));
        // Return matching-sized classifier results so the size-check passes. persistPair
        // will throw (mocked DSL returns null for fetchOne), but those exceptions are
        // caught and tallied as pair-failures; we only care about the dispatch.
        BatchPackagingResult rA = new BatchPackagingResult("3923", "A",
                List.of(), BatchPackagingResult.STRATEGY_UNRESOLVED, "v1");
        BatchPackagingResult rB = new BatchPackagingResult("3924", "B",
                List.of(), BatchPackagingResult.STRATEGY_UNRESOLVED, "v1");
        when(classifierService.classify(any(), eq(TENANT_ID))).thenReturn(List.of(rA, rB));

        service.processJob(JOB_ID, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "12345678");

        // setTotalPairs(2) because line1 and line3 dedup to one pair.
        verify(bootstrapJobRepository).setTotalPairs(JOB_ID, 2);
        // Classifier was called with a 2-element pair list.
        verify(classifierService).classify(argThat(list -> list.size() == 2), eq(TENANT_ID));
    }

    // ── AC #32h: cancellation mid-batch — CANCELLED status exits worker cleanly ─

    @Test
    void processJob_cancellationMidBatch_exitsWithoutCompleting() {
        var line1 = new InvoiceLineItem(1, "A", null, null, null, null, null, "3923", null, null);
        var detail = new InvoiceDetail("INV-cancel", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND,
                List.of(line1), null, null);
        var summary = new InvoiceSummary("INV-cancel", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND);

        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-cancel")).thenReturn(detail);
        when(usageService.getMonthlyCap()).thenReturn(1000);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0);
        // Between the RUNNING transition and the chunk loop, the user hits DELETE.
        when(bootstrapJobRepository.readStatus(JOB_ID))
                .thenReturn(Optional.of(BootstrapJobStatus.CANCELLED));

        service.processJob(JOB_ID, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "12345678");

        // Classifier must NOT be called; the worker should exit after seeing CANCELLED.
        verifyNoInteractions(classifierService);
        // No terminal transition beyond CANCELLED — worker does not touch the job row.
        verify(bootstrapJobRepository, never()).transitionStatus(eq(JOB_ID), eq(BootstrapJobStatus.COMPLETED), any());
        verify(bootstrapJobRepository, never()).transitionStatus(eq(JOB_ID), eq(BootstrapJobStatus.FAILED), any());
    }

    // ── R4 / cancel race: transitionStatus returns 0 (already terminal) → worker exits ─

    @Test
    void processJob_raceCancel_beforeRunningTransition_skipsWork() {
        // Simulates the narrow race where the user cancels between startJob returning 202
        // and the @Async worker starting: transitionStatus(RUNNING) finds the row CANCELLED
        // and returns 0. The worker must exit without calling NAV.
        when(bootstrapJobRepository.transitionStatus(JOB_ID, BootstrapJobStatus.RUNNING, null))
                .thenReturn(0);

        service.processJob(JOB_ID, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "12345678-1-11");

        verifyNoInteractions(dataSourceService);
        verifyNoInteractions(classifierService);
    }

    // ── R6: FAILED_PARTIAL error_message includes first-3 failure reasons ─────
    //
    // Direct unit test of completeJob via reflection would introduce brittle assertions;
    // this scenario is implicitly exercised by the integration test. Kept as a smoke
    // check here to assert the truncate() helper's ceiling behavior. The payload below
    // is deliberately shorter than 999 chars to document the happy-path contract.

    @Test
    void completeJob_errorMessageUnder1000_preservedVerbatim() throws Exception {
        // truncate() is a private static helper; invoke via reflection to validate contract.
        java.lang.reflect.Method m = InvoiceDrivenRegistryBootstrapService.class
                .getDeclaredMethod("truncate", String.class);
        m.setAccessible(true);

        String shortMsg = "12/34 pairs failed: vtsz=3923: X; vtsz=3924: Y; vtsz=3925: Z";
        assertThat(m.invoke(null, shortMsg)).isEqualTo(shortMsg);

        String longMsg = "a".repeat(2000);
        String result = (String) m.invoke(null, longMsg);
        assertThat(result).hasSize(999);
        assertThat(result).matches("^a+$");

        assertThat(m.invoke(null, (Object) null)).isNull();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition def) {
            return new SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) {}
        @Override public void rollback(TransactionStatus status) {}
    }

    private BootstrapJobRecord mockJobRecord(UUID id, BootstrapJobStatus status) {
        return new BootstrapJobRecord(id, TENANT_ID, status,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                0, 0, 0, 0, 0, 0, USER_ID, null,
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now(), null);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), name);
            throw e;
        }
    }
}
