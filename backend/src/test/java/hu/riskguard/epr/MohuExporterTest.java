package hu.riskguard.epr;

import hu.riskguard.epr.api.dto.ExportLineRequest;
import hu.riskguard.epr.domain.MohuExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MohuExporter} — MOHU CSV generation.
 * Story 5.3, Task 6.1.
 */
class MohuExporterTest {

    private MohuExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new MohuExporter();
    }

    // ─── Test 1 — BOM prefix ───────────────────────────────────────────────

    @Test
    void shouldStartWithUtf8Bom() {
        byte[] bytes = exporter.generate(List.of(kartondobozLine()));
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    // ─── Test 2 — Header row ──────────────────────────────────────────────

    @Test
    void shouldHaveCorrectHeaderRow() {
        byte[] bytes = exporter.generate(List.of(kartondobozLine()));
        String[] lines = parseLines(bytes);
        assertThat(lines[0]).isEqualTo("KF kód;Megnevezés;Darabszám (db);Összsúly (kg);Díj (HUF)");
    }

    // ─── Test 3 — Golden row ──────────────────────────────────────────────

    @Test
    void row_kartondobozA_1000pcs() {
        // 1000 pcs × 120g = 120 kg; fee 25800 HUF
        byte[] bytes = exporter.generate(List.of(kartondobozLine()));
        String[] lines = parseLines(bytes);
        assertThat(lines[1]).isEqualTo("11010101;Kartondoboz A;1000;120,000000;25800");
    }

    // ─── Test 4 — Rounding edge case ─────────────────────────────────────

    @Test
    void row_kisDoboz_1pc() {
        // 1 pc × 50g = 0.05 kg; fee 11 HUF
        ExportLineRequest line = new ExportLineRequest(
                UUID.randomUUID(), "11010202", "Kis doboz", 1,
                new BigDecimal("0.050000"), new BigDecimal("11"));
        byte[] bytes = exporter.generate(List.of(line));
        String[] lines = parseLines(bytes);
        assertThat(lines[1]).isEqualTo("11010202;Kis doboz;1;0,050000;11");
    }

    // ─── Test 5 — Multi-row count ─────────────────────────────────────────

    @Test
    void multiRow_rowCountEqualsInputLineCount() {
        ExportLineRequest line1 = kartondobozLine();
        ExportLineRequest line2 = new ExportLineRequest(
                UUID.randomUUID(), "11010202", "Kis doboz", 1,
                new BigDecimal("0.050000"), new BigDecimal("11"));
        ExportLineRequest line3 = new ExportLineRequest(
                UUID.randomUUID(), "11010303", "Fólia B", 100000,
                new BigDecimal("500.000000"), new BigDecimal("65000"));

        byte[] bytes = exporter.generate(List.of(line1, line2, line3));
        String[] lines = parseLines(bytes);
        // lines[0] = header, lines[1..3] = data
        assertThat(lines).hasSize(4); // 1 header + 3 data rows
    }

    // ─── Test 6 — Semicolon in name field (RFC 4180 quoting) ─────────────

    @Test
    void semicolonInNameShouldBeQuoted() {
        ExportLineRequest line = new ExportLineRequest(
                UUID.randomUUID(), "11010101", "Box; extra", 10,
                new BigDecimal("1.200000"), new BigDecimal("258"));
        byte[] bytes = exporter.generate(List.of(line));
        String[] lines = parseLines(bytes);
        // The name field should be quoted because it contains a semicolon
        assertThat(lines[1]).isEqualTo("11010101;\"Box; extra\";10;1,200000;258");
    }

    @Test
    void doubleQuoteInNameShouldBeEscaped() {
        ExportLineRequest line = new ExportLineRequest(
                UUID.randomUUID(), "11010101", "Box \"A\"", 5,
                new BigDecimal("0.600000"), new BigDecimal("129"));
        byte[] bytes = exporter.generate(List.of(line));
        String[] lines = parseLines(bytes);
        assertThat(lines[1]).isEqualTo("11010101;\"Box \"\"A\"\"\";5;0,600000;129");
    }

    @Test
    void carriageReturnInNameShouldBeQuoted() {
        ExportLineRequest line = new ExportLineRequest(
                UUID.randomUUID(), "11010101", "Box\rA", 10,
                new BigDecimal("1.200000"), new BigDecimal("258"));
        byte[] bytes = exporter.generate(List.of(line));
        String[] lines = parseLines(bytes);
        assertThat(lines[1]).isEqualTo("11010101;\"Box\rA\";10;1,200000;258");
    }

    @Test
    void noTrailingNewlineAfterLastRow() {
        byte[] bytes = exporter.generate(List.of(kartondobozLine()));
        String raw = new String(bytes, StandardCharsets.UTF_8);
        assertThat(raw).doesNotEndWith("\n");
    }

    @Test
    void emptyLinesProducesHeaderOnly() {
        byte[] bytes = exporter.generate(List.of());
        String[] lines = parseLines(bytes);
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).isEqualTo("KF kód;Megnevezés;Darabszám (db);Összsúly (kg);Díj (HUF)");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private static String[] parseLines(byte[] bytes) {
        String raw = new String(bytes, StandardCharsets.UTF_8);
        if (raw.startsWith("\uFEFF")) raw = raw.substring(1);
        return raw.split("\n");
    }

    private static ExportLineRequest kartondobozLine() {
        return new ExportLineRequest(
                UUID.randomUUID(), "11010101", "Kartondoboz A", 1000,
                new BigDecimal("120.000000"), new BigDecimal("25800"));
    }
}
