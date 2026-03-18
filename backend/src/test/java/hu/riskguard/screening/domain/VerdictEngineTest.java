package hu.riskguard.screening.domain;

import hu.riskguard.jooq.enums.VerdictConfidence;
import hu.riskguard.jooq.enums.VerdictStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Golden Case regression test suite for {@link VerdictEngine}.
 * 50+ parameterized cases covering every combination of status priority, source availability,
 * freshness tiers, risk signals, and edge cases.
 *
 * <p>Pure unit tests — no Spring context required.
 */
class VerdictEngineTest {

    private static final FreshnessConfig CONFIG = new FreshnessConfig(6, 24, 48);

    /** Fixed evaluation time — all tests use this as "now" for deterministic freshness computation. */
    private static final OffsetDateTime EVAL_TIME = OffsetDateTime.now();

    // --- Helper builders ---

    private static SnapshotData clean() {
        return new SnapshotData(false, false, false, Map.of(
                "nav-debt", SourceStatus.AVAILABLE,
                "e-cegjegyzek", SourceStatus.AVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData withDebt() {
        return new SnapshotData(false, true, false, Map.of(
                "nav-debt", SourceStatus.AVAILABLE,
                "e-cegjegyzek", SourceStatus.AVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData withInsolvency() {
        return new SnapshotData(false, false, true, Map.of(
                "nav-debt", SourceStatus.AVAILABLE,
                "e-cegjegyzek", SourceStatus.AVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData withDebtAndInsolvency() {
        return new SnapshotData(false, true, true, Map.of(
                "nav-debt", SourceStatus.AVAILABLE,
                "e-cegjegyzek", SourceStatus.AVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData suspended() {
        return new SnapshotData(true, false, false, Map.of(
                "nav-debt", SourceStatus.AVAILABLE,
                "e-cegjegyzek", SourceStatus.AVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData suspendedWithDebt() {
        return new SnapshotData(true, true, false, Map.of(
                "nav-debt", SourceStatus.AVAILABLE,
                "e-cegjegyzek", SourceStatus.AVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData suspendedAllUnavailable() {
        return new SnapshotData(true, false, false, Map.of(
                "nav-debt", SourceStatus.UNAVAILABLE,
                "e-cegjegyzek", SourceStatus.UNAVAILABLE,
                "cegkozlony", SourceStatus.UNAVAILABLE));
    }

    private static SnapshotData suspendedWithDebtAndInsolvency() {
        return new SnapshotData(true, true, true, Map.of(
                "nav-debt", SourceStatus.AVAILABLE,
                "e-cegjegyzek", SourceStatus.AVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData navUnavailable() {
        return new SnapshotData(false, false, false, Map.of(
                "nav-debt", SourceStatus.UNAVAILABLE,
                "e-cegjegyzek", SourceStatus.AVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData cegjegyzekUnavailable() {
        return new SnapshotData(false, false, false, Map.of(
                "nav-debt", SourceStatus.AVAILABLE,
                "e-cegjegyzek", SourceStatus.UNAVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData cegkozlonyUnavailable() {
        return new SnapshotData(false, false, false, Map.of(
                "nav-debt", SourceStatus.AVAILABLE,
                "e-cegjegyzek", SourceStatus.AVAILABLE,
                "cegkozlony", SourceStatus.UNAVAILABLE));
    }

    private static SnapshotData twoUnavailable() {
        return new SnapshotData(false, false, false, Map.of(
                "nav-debt", SourceStatus.UNAVAILABLE,
                "e-cegjegyzek", SourceStatus.UNAVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData allUnavailable() {
        return new SnapshotData(false, false, false, Map.of(
                "nav-debt", SourceStatus.UNAVAILABLE,
                "e-cegjegyzek", SourceStatus.UNAVAILABLE,
                "cegkozlony", SourceStatus.UNAVAILABLE));
    }

    private static SnapshotData debtWithNavUnavailable() {
        return new SnapshotData(false, true, false, Map.of(
                "nav-debt", SourceStatus.UNAVAILABLE,
                "e-cegjegyzek", SourceStatus.AVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData debtWithPartialUnavailable() {
        return new SnapshotData(false, true, false, Map.of(
                "nav-debt", SourceStatus.AVAILABLE,
                "e-cegjegyzek", SourceStatus.UNAVAILABLE,
                "cegkozlony", SourceStatus.AVAILABLE));
    }

    private static SnapshotData insolvencyWithPartialUnavailable() {
        return new SnapshotData(false, false, true, Map.of(
                "nav-debt", SourceStatus.AVAILABLE,
                "e-cegjegyzek", SourceStatus.AVAILABLE,
                "cegkozlony", SourceStatus.UNAVAILABLE));
    }

    private static OffsetDateTime hoursAgo(long hours) {
        return EVAL_TIME.minusHours(hours);
    }

    // ======================================================================
    // Category 1: Status Priority (10+ cases)
    // ======================================================================

    @Nested
    @DisplayName("Category 1: Status Priority")
    class StatusPriority {

        static Stream<Arguments> statusPriorityCases() {
            return Stream.of(
                    // name, snapshotData, checkedAt, expectedStatus
                    Arguments.of("clean data → RELIABLE", clean(), hoursAgo(1), VerdictStatus.RELIABLE),
                    Arguments.of("debt → AT_RISK", withDebt(), hoursAgo(1), VerdictStatus.AT_RISK),
                    Arguments.of("insolvency → AT_RISK", withInsolvency(), hoursAgo(1), VerdictStatus.AT_RISK),
                    Arguments.of("debt + insolvency → AT_RISK", withDebtAndInsolvency(), hoursAgo(1), VerdictStatus.AT_RISK),
                    Arguments.of("suspended → TAX_SUSPENDED", suspended(), hoursAgo(1), VerdictStatus.TAX_SUSPENDED),
                    Arguments.of("suspended + debt → TAX_SUSPENDED", suspendedWithDebt(), hoursAgo(1), VerdictStatus.TAX_SUSPENDED),
                    Arguments.of("suspended + all unavailable → TAX_SUSPENDED", suspendedAllUnavailable(), hoursAgo(1), VerdictStatus.TAX_SUSPENDED),
                    Arguments.of("suspended + debt + insolvency → TAX_SUSPENDED", suspendedWithDebtAndInsolvency(), hoursAgo(1), VerdictStatus.TAX_SUSPENDED),
                    Arguments.of("debt with nav unavailable → AT_RISK", debtWithNavUnavailable(), hoursAgo(1), VerdictStatus.AT_RISK),
                    Arguments.of("debt with partial unavailable → AT_RISK", debtWithPartialUnavailable(), hoursAgo(1), VerdictStatus.AT_RISK),
                    Arguments.of("insolvency with cegkozlony unavailable → AT_RISK", insolvencyWithPartialUnavailable(), hoursAgo(1), VerdictStatus.AT_RISK)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("statusPriorityCases")
        void evaluateStatusPriority(String name, SnapshotData data, OffsetDateTime checkedAt, VerdictStatus expected) {
            VerdictResult result = VerdictEngine.evaluate(data, checkedAt, CONFIG, EVAL_TIME);
            assertThat(result.status()).as(name).isEqualTo(expected);
        }
    }

    // ======================================================================
    // Category 2: Source Availability (10+ cases)
    // ======================================================================

    @Nested
    @DisplayName("Category 2: Source Availability")
    class SourceAvailabilityCases {

        static Stream<Arguments> sourceAvailabilityCases() {
            return Stream.of(
                    // name, snapshotData, expectedStatus
                    Arguments.of("nav unavailable → INCOMPLETE", navUnavailable(), VerdictStatus.INCOMPLETE),
                    Arguments.of("e-cegjegyzek unavailable → INCOMPLETE", cegjegyzekUnavailable(), VerdictStatus.INCOMPLETE),
                    Arguments.of("cegkozlony unavailable → INCOMPLETE", cegkozlonyUnavailable(), VerdictStatus.INCOMPLETE),
                    Arguments.of("two unavailable → INCOMPLETE", twoUnavailable(), VerdictStatus.INCOMPLETE),
                    Arguments.of("all unavailable → INCOMPLETE", allUnavailable(), VerdictStatus.INCOMPLETE),
                    Arguments.of("all available clean → RELIABLE", clean(), VerdictStatus.RELIABLE),
                    Arguments.of("debt despite nav unavailable → AT_RISK", debtWithNavUnavailable(), VerdictStatus.AT_RISK),
                    Arguments.of("debt despite partial unavailable → AT_RISK", debtWithPartialUnavailable(), VerdictStatus.AT_RISK),
                    Arguments.of("insolvency despite partial unavailable → AT_RISK", insolvencyWithPartialUnavailable(), VerdictStatus.AT_RISK),
                    Arguments.of("risk overrides source unavailability",
                            new SnapshotData(false, true, true, Map.of(
                                    "nav-debt", SourceStatus.UNAVAILABLE,
                                    "e-cegjegyzek", SourceStatus.UNAVAILABLE,
                                    "cegkozlony", SourceStatus.UNAVAILABLE)),
                            VerdictStatus.AT_RISK)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("sourceAvailabilityCases")
        void evaluateSourceAvailability(String name, SnapshotData data, VerdictStatus expected) {
            VerdictResult result = VerdictEngine.evaluate(data, hoursAgo(1), CONFIG, EVAL_TIME);
            assertThat(result.status()).as(name).isEqualTo(expected);
        }
    }

    // ======================================================================
    // Category 3: Freshness (10+ cases)
    // ======================================================================

    @Nested
    @DisplayName("Category 3: Freshness")
    class FreshnessCases {

        static Stream<Arguments> freshnessCases() {
            return Stream.of(
                    // name, hoursAgo, expectedConfidence, expectedStatus
                    Arguments.of("1h → FRESH", 1L, VerdictConfidence.FRESH, VerdictStatus.RELIABLE),
                    Arguments.of("3h → FRESH", 3L, VerdictConfidence.FRESH, VerdictStatus.RELIABLE),
                    Arguments.of("5h → FRESH", 5L, VerdictConfidence.FRESH, VerdictStatus.RELIABLE),
                    Arguments.of("7h → STALE", 7L, VerdictConfidence.STALE, VerdictStatus.RELIABLE),
                    Arguments.of("12h → STALE", 12L, VerdictConfidence.STALE, VerdictStatus.RELIABLE),
                    Arguments.of("24h → STALE", 24L, VerdictConfidence.STALE, VerdictStatus.RELIABLE),
                    Arguments.of("36h → STALE", 36L, VerdictConfidence.STALE, VerdictStatus.RELIABLE),
                    Arguments.of("47h → STALE", 47L, VerdictConfidence.STALE, VerdictStatus.RELIABLE),
                    Arguments.of("48h → UNAVAILABLE → INCOMPLETE", 48L, VerdictConfidence.UNAVAILABLE, VerdictStatus.INCOMPLETE),
                    Arguments.of("100h → UNAVAILABLE → INCOMPLETE", 100L, VerdictConfidence.UNAVAILABLE, VerdictStatus.INCOMPLETE),
                    Arguments.of("720h (30 days) → UNAVAILABLE → INCOMPLETE", 720L, VerdictConfidence.UNAVAILABLE, VerdictStatus.INCOMPLETE)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("freshnessCases")
        void evaluateFreshness(String name, long hours, VerdictConfidence expectedConf, VerdictStatus expectedStatus) {
            VerdictResult result = VerdictEngine.evaluate(clean(), hoursAgo(hours), CONFIG, EVAL_TIME);
            assertThat(result.confidence()).as(name + " confidence").isEqualTo(expectedConf);
            assertThat(result.status()).as(name + " status").isEqualTo(expectedStatus);
        }

        @ParameterizedTest(name = "null checkedAt → UNAVAILABLE → INCOMPLETE")
        @MethodSource("nullCheckedAtCase")
        void nullCheckedAtForcesIncomplete(String name) {
            VerdictResult result = VerdictEngine.evaluate(clean(), null, CONFIG, EVAL_TIME);
            assertThat(result.confidence()).isEqualTo(VerdictConfidence.UNAVAILABLE);
            assertThat(result.status()).isEqualTo(VerdictStatus.INCOMPLETE);
            assertThat(result.riskSignals()).contains("DATA_EXPIRED");
        }

        static Stream<Arguments> nullCheckedAtCase() {
            return Stream.of(Arguments.of("null checkedAt"));
        }
    }

    // ======================================================================
    // Category 4: Risk Signals (5+ cases)
    // ======================================================================

    @Nested
    @DisplayName("Category 4: Risk Signals")
    class RiskSignals {

        static Stream<Arguments> riskSignalCases() {
            return Stream.of(
                    Arguments.of("RELIABLE has empty signals", clean(), new String[0]),
                    Arguments.of("debt has PUBLIC_DEBT_DETECTED", withDebt(), new String[]{"PUBLIC_DEBT_DETECTED"}),
                    Arguments.of("insolvency has INSOLVENCY_PROCEEDINGS_ACTIVE", withInsolvency(), new String[]{"INSOLVENCY_PROCEEDINGS_ACTIVE"}),
                    Arguments.of("debt + insolvency accumulate both signals", withDebtAndInsolvency(),
                            new String[]{"PUBLIC_DEBT_DETECTED", "INSOLVENCY_PROCEEDINGS_ACTIVE"}),
                    Arguments.of("suspended has TAX_NUMBER_SUSPENDED", suspended(), new String[]{"TAX_NUMBER_SUSPENDED"}),
                    Arguments.of("nav unavailable has SOURCE_UNAVAILABLE:nav-debt", navUnavailable(), new String[]{"SOURCE_UNAVAILABLE:nav-debt"}),
                    Arguments.of("two unavailable have two SOURCE_UNAVAILABLE signals", twoUnavailable(),
                            new String[]{"SOURCE_UNAVAILABLE:nav-debt", "SOURCE_UNAVAILABLE:e-cegjegyzek"})
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("riskSignalCases")
        void evaluateRiskSignals(String name, SnapshotData data, String[] expectedSignals) {
            VerdictResult result = VerdictEngine.evaluate(data, hoursAgo(1), CONFIG, EVAL_TIME);
            if (expectedSignals.length == 0) {
                assertThat(result.riskSignals()).as(name).isEmpty();
            } else {
                assertThat(result.riskSignals()).as(name).contains(expectedSignals);
            }
        }
    }

    // ======================================================================
    // Category 5: Edge Cases (5+ cases)
    // ======================================================================

    @Nested
    @DisplayName("Category 5: Edge Cases")
    class EdgeCases {

        static Stream<Arguments> edgeCases() {
            return Stream.of(
                    Arguments.of("empty sourceAvailability map",
                            new SnapshotData(false, false, false, Map.of()),
                            hoursAgo(1), VerdictStatus.RELIABLE, VerdictConfidence.FRESH),
                    Arguments.of("exactly 0h → FRESH",
                            clean(), EVAL_TIME, VerdictStatus.RELIABLE, VerdictConfidence.FRESH),
                    Arguments.of("exactly 6h boundary → STALE",
                            clean(), hoursAgo(6), VerdictStatus.RELIABLE, VerdictConfidence.STALE),
                    Arguments.of("exactly 48h boundary → UNAVAILABLE → INCOMPLETE",
                            clean(), hoursAgo(48), VerdictStatus.INCOMPLETE, VerdictConfidence.UNAVAILABLE),
                    Arguments.of("suspended with stale data → TAX_SUSPENDED + STALE confidence",
                            suspended(), hoursAgo(12), VerdictStatus.TAX_SUSPENDED, VerdictConfidence.STALE),
                    Arguments.of("suspended with expired data → TAX_SUSPENDED + UNAVAILABLE confidence",
                            suspended(), hoursAgo(100), VerdictStatus.TAX_SUSPENDED, VerdictConfidence.UNAVAILABLE),
                    Arguments.of("AT_RISK with STALE confidence",
                            withDebt(), hoursAgo(12), VerdictStatus.AT_RISK, VerdictConfidence.STALE),
                    Arguments.of("suspended with null checkedAt → TAX_SUSPENDED + UNAVAILABLE",
                            suspended(), null, VerdictStatus.TAX_SUSPENDED, VerdictConfidence.UNAVAILABLE),
                    Arguments.of("debt+insolvency+all-unavailable → AT_RISK (risk overrides all)",
                            new SnapshotData(false, true, true, Map.of(
                                    "nav-debt", SourceStatus.UNAVAILABLE,
                                    "e-cegjegyzek", SourceStatus.UNAVAILABLE,
                                    "cegkozlony", SourceStatus.UNAVAILABLE)),
                            hoursAgo(1), VerdictStatus.AT_RISK, VerdictConfidence.FRESH),
                    Arguments.of("exactly 5h59m → FRESH (just under boundary)",
                            clean(), EVAL_TIME.minusHours(5).minusMinutes(59),
                            VerdictStatus.RELIABLE, VerdictConfidence.FRESH),
                    Arguments.of("exactly 47h59m → STALE (just under UNAVAILABLE boundary)",
                            clean(), EVAL_TIME.minusHours(47).minusMinutes(59),
                            VerdictStatus.RELIABLE, VerdictConfidence.STALE)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("edgeCases")
        void evaluateEdgeCases(String name, SnapshotData data, OffsetDateTime checkedAt,
                               VerdictStatus expectedStatus, VerdictConfidence expectedConf) {
            VerdictResult result = VerdictEngine.evaluate(data, checkedAt, CONFIG, EVAL_TIME);
            assertThat(result.status()).as(name + " status").isEqualTo(expectedStatus);
            assertThat(result.confidence()).as(name + " confidence").isEqualTo(expectedConf);
        }
    }

    // ======================================================================
    // Category 6: Null Guard Tests
    // ======================================================================

    @Nested
    @DisplayName("Category 6: Null Guards")
    class NullGuards {

        @org.junit.jupiter.api.Test
        @DisplayName("null SnapshotData throws NullPointerException")
        void nullSnapshotDataThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> VerdictEngine.evaluate(null, hoursAgo(1), CONFIG, EVAL_TIME))
                    .withMessage("SnapshotData must not be null");
        }

        @org.junit.jupiter.api.Test
        @DisplayName("null FreshnessConfig throws NullPointerException")
        void nullFreshnessConfigThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> VerdictEngine.evaluate(clean(), hoursAgo(1), null, EVAL_TIME))
                    .withMessage("FreshnessConfig must not be null");
        }

        @org.junit.jupiter.api.Test
        @DisplayName("null evaluationTime throws NullPointerException")
        void nullEvaluationTimeThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> VerdictEngine.evaluate(clean(), hoursAgo(1), CONFIG, null))
                    .withMessage("evaluationTime must not be null");
        }
    }
}
