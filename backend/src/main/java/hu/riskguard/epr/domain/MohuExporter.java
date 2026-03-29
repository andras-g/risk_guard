package hu.riskguard.epr.domain;

import hu.riskguard.core.security.ExportLocale;
import hu.riskguard.epr.api.dto.ExportLineRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Generates MOHU-compliant CSV exports for quarterly EPR filing.
 *
 * <p>Output format (schema_version "2026.1", config_version 1):
 * <ul>
 *   <li>UTF-8 BOM prefix (3 bytes: 0xEF 0xBB 0xBF)</li>
 *   <li>Semicolon delimiter</li>
 *   <li>Hungarian decimal separator (comma) for kg values</li>
 *   <li>LF line endings</li>
 * </ul>
 */
@Component
@ExportLocale("hu")
public class MohuExporter {

    private static final String HEADER = "KF kód;Megnevezés;Darabszám (db);Összsúly (kg);Díj (HUF)";
    private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    /**
     * Generate a MOHU-compliant CSV byte array for the given export lines.
     *
     * @param lines the filing lines to export (non-null, may be empty)
     * @return UTF-8 BOM + CSV header + data rows, LF-delimited, no trailing newline
     */
    public byte[] generate(List<ExportLineRequest> lines) {
        var sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        for (var line : lines) {
            sb.append(escapeField(line.kfCode())).append(';')
              .append(escapeField(line.name())).append(';')
              .append(line.quantityPcs()).append(';')
              .append(String.format("%.6f", line.totalWeightKg()).replace('.', ',')).append(';')
              .append(line.feeAmountHuf().intValue()).append('\n');
        }
        // Strip the trailing newline from the last row
        if (!lines.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        byte[] csvBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + csvBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(csvBytes, 0, result, UTF8_BOM.length, csvBytes.length);
        return result;
    }

    /**
     * Minimal RFC 4180 quoting for a single CSV field.
     * Wraps in double-quotes if the value contains semicolons, double-quotes, or newlines.
     * Internal double-quotes are escaped by doubling them.
     */
    private String escapeField(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
