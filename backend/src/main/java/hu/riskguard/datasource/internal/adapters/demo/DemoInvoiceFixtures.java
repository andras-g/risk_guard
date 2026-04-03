package hu.riskguard.datasource.internal.adapters.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo invoice fixtures providing realistic Hungarian invoice data for future EPR module.
 * Each fixture company has 20-50 invoices per quarter with realistic line items.
 *
 * <p>This class seeds the data for future {@code QueryInvoiceDigest}/{@code QueryInvoiceData}
 * needs when the NAV Online Számla adapter is implemented.
 *
 * <p>Invoice data structure mirrors the NAV Online Számla API v3 invoice format.
 */
public final class DemoInvoiceFixtures {

    private DemoInvoiceFixtures() {
        // Utility class — no instantiation
    }

    /**
     * Per-invoice fixture record (mirrors NAV invoice digest + detail).
     */
    public record InvoiceFixture(
            String invoiceNumber,
            LocalDate issueDate,
            String supplierTaxNumber,
            String customerTaxNumber,
            String direction, // OUTBOUND or INBOUND
            List<LineItemFixture> lineItems
    ) {}

    /**
     * Per-line-item fixture record (mirrors NAV invoice line item).
     */
    public record LineItemFixture(
            int lineNumber,
            String description,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal unitPrice,
            BigDecimal netAmount,
            BigDecimal vatRate,
            String vtszCode
    ) {}

    /** Map of 8-digit tax number → list of invoices for Q1 2026. */
    private static final Map<String, List<InvoiceFixture>> INVOICES = new LinkedHashMap<>();

    static {
        // Generate invoices for each fixture company
        INVOICES.put("12345678", generateTradeCompanyInvoices("12345678"));
        INVOICES.put("99887766", generateConstructionCompanyInvoices("99887766"));
        INVOICES.put("11223344", generateServiceCompanyInvoices("11223344"));
        INVOICES.put("55667788", generateManufacturingCompanyInvoices("55667788"));
        INVOICES.put("44556677", generateMixedCompanyInvoices("44556677"));
        INVOICES.put("33445566", generateSuspendedCompanyInvoices("33445566"));
        INVOICES.put("77889900", generateIncompleteCompanyInvoices("77889900"));
        INVOICES.put("22334455", generateStartupCompanyInvoices("22334455"));
    }

    /**
     * Get invoices for a given 8-digit tax number.
     *
     * @param taxNumber8 the first 8 digits of the tax number
     * @return list of invoices, or empty list for unknown companies
     */
    public static List<InvoiceFixture> getInvoices(String taxNumber8) {
        return INVOICES.getOrDefault(taxNumber8, List.of());
    }

    /**
     * Get invoices for a given tax number — alias used by {@code DataSourceService} facade.
     * Normalises the tax number to 8 digits before lookup.
     *
     * @param taxNumber tax number (may include dashes or be longer than 8 digits)
     * @return list of invoices, or empty list for unknown companies
     */
    public static List<InvoiceFixture> getForTaxNumber(String taxNumber) {
        String normalised = taxNumber == null ? "" : taxNumber.replace("-", "");
        if (normalised.length() > 8) {
            normalised = normalised.substring(0, 8);
        }
        return INVOICES.getOrDefault(normalised, List.of());
    }

    /**
     * Get all invoices across all companies (used for invoice-number lookup in queryInvoiceDetails).
     *
     * @return flat list of all invoices in the demo dataset
     */
    public static List<InvoiceFixture> getAllFixtures() {
        return INVOICES.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    // --- Invoice generators for each company scenario ---

    private static List<InvoiceFixture> generateTradeCompanyInvoices(String taxNumber) {
        // Példa Kereskedelmi Kft. — trade company selling hardware/packaging
        List<InvoiceFixture> invoices = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2026, 1, 5);

        for (int i = 1; i <= 35; i++) {
            LocalDate issueDate = baseDate.plusDays((long) i * 2);
            String direction = i % 3 == 0 ? "INBOUND" : "OUTBOUND";
            String counterparty = direction.equals("OUTBOUND") ? "87654321" : "11112222";

            List<LineItemFixture> items = new ArrayList<>();
            items.add(new LineItemFixture(1, "Csavar M6x30 (100db)", BigDecimal.valueOf(50),
                    "DARAB", BigDecimal.valueOf(120), BigDecimal.valueOf(6000),
                    BigDecimal.valueOf(27), "73181500"));
            items.add(new LineItemFixture(2, "PET palack 0,5L", BigDecimal.valueOf(200),
                    "DARAB", BigDecimal.valueOf(45), BigDecimal.valueOf(9000),
                    BigDecimal.valueOf(27), "39233000"));
            if (i % 5 == 0) {
                items.add(new LineItemFixture(3, "Kartondoboz 40x30x20", BigDecimal.valueOf(100),
                        "DARAB", BigDecimal.valueOf(350), BigDecimal.valueOf(35000),
                        BigDecimal.valueOf(27), "48191000"));
            }

            invoices.add(new InvoiceFixture(
                    "PKK-2026-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : counterparty,
                    direction.equals("OUTBOUND") ? counterparty : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateConstructionCompanyInvoices(String taxNumber) {
        // Megbízható Építő Zrt. — construction materials
        List<InvoiceFixture> invoices = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2026, 1, 3);

        for (int i = 1; i <= 28; i++) {
            LocalDate issueDate = baseDate.plusDays((long) i * 3);
            String direction = i % 4 == 0 ? "INBOUND" : "OUTBOUND";
            String counterparty = "33334444";

            List<LineItemFixture> items = new ArrayList<>();
            items.add(new LineItemFixture(1, "Betonacél Ø12 B500B (6m)", BigDecimal.valueOf(20),
                    "TONNA", BigDecimal.valueOf(285000), BigDecimal.valueOf(5700000),
                    BigDecimal.valueOf(27), "72142000"));
            items.add(new LineItemFixture(2, "Cement CEM II/B-M 32,5 R (25kg)", BigDecimal.valueOf(80),
                    "ZSÁK", BigDecimal.valueOf(1850), BigDecimal.valueOf(148000),
                    BigDecimal.valueOf(27), "25232900"));

            invoices.add(new InvoiceFixture(
                    "MEZ-2026-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : counterparty,
                    direction.equals("OUTBOUND") ? counterparty : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateServiceCompanyInvoices(String taxNumber) {
        // Adós Szolgáltató Bt. — IT services
        // Base: 2026-01-08, step: 3 days → last invoice 2026-01-08 + 21*3 = 2026-03-16 (within Q1)
        List<InvoiceFixture> invoices = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2026, 1, 8);

        for (int i = 1; i <= 22; i++) {
            LocalDate issueDate = baseDate.plusDays((long) (i - 1) * 3);
            String direction = i % 5 == 0 ? "INBOUND" : "OUTBOUND";

            List<LineItemFixture> items = new ArrayList<>();
            items.add(new LineItemFixture(1, "IT tanácsadás (óradíjas)", BigDecimal.valueOf(40),
                    "ÓRA", BigDecimal.valueOf(15000), BigDecimal.valueOf(600000),
                    BigDecimal.valueOf(27), "62020000"));
            if (i % 3 == 0) {
                items.add(new LineItemFixture(2, "Szoftver licenc (havi)", BigDecimal.ONE,
                        "DARAB", BigDecimal.valueOf(89000), BigDecimal.valueOf(89000),
                        BigDecimal.valueOf(27), "85234000"));
            }

            invoices.add(new InvoiceFixture(
                    "ASB-2026-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : "55556666",
                    direction.equals("OUTBOUND") ? "55556666" : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateManufacturingCompanyInvoices(String taxNumber) {
        // Csődben Lévő Kft. — food manufacturing (insolvency)
        List<InvoiceFixture> invoices = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2026, 1, 2);

        for (int i = 1; i <= 42; i++) {
            LocalDate issueDate = baseDate.plusDays((long) i * 2);
            String direction = i % 2 == 0 ? "INBOUND" : "OUTBOUND";

            List<LineItemFixture> items = new ArrayList<>();
            items.add(new LineItemFixture(1, "Kenyér (fehér, 500g)", BigDecimal.valueOf(500),
                    "DARAB", BigDecimal.valueOf(320), BigDecimal.valueOf(160000),
                    BigDecimal.valueOf(5), "19059090"));
            items.add(new LineItemFixture(2, "Búzaliszt BL-55 (1kg)", BigDecimal.valueOf(300),
                    "DARAB", BigDecimal.valueOf(280), BigDecimal.valueOf(84000),
                    BigDecimal.valueOf(18), "11010015"));

            invoices.add(new InvoiceFixture(
                    "CLK-2026-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : "77778888",
                    direction.equals("OUTBOUND") ? "77778888" : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateMixedCompanyInvoices(String taxNumber) {
        // Hátralékos és Csődös Kft. — mixed goods
        List<InvoiceFixture> invoices = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2026, 1, 10);

        for (int i = 1; i <= 25; i++) {
            LocalDate issueDate = baseDate.plusDays((long) i * 3);
            String direction = i <= 15 ? "OUTBOUND" : "INBOUND";

            List<LineItemFixture> items = new ArrayList<>();
            items.add(new LineItemFixture(1, "Alumínium profil 40x40 (3m)", BigDecimal.valueOf(30),
                    "DARAB", BigDecimal.valueOf(4200), BigDecimal.valueOf(126000),
                    BigDecimal.valueOf(27), "76042100"));

            invoices.add(new InvoiceFixture(
                    "HCK-2026-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : "99990000",
                    direction.equals("OUTBOUND") ? "99990000" : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateSuspendedCompanyInvoices(String taxNumber) {
        // Felfüggesztett Adószámú Kft. — minimal invoices (suspended)
        List<InvoiceFixture> invoices = new ArrayList<>();
        // Only a few invoices before suspension
        for (int i = 1; i <= 5; i++) {
            LocalDate issueDate = LocalDate.of(2026, 1, i * 5);
            invoices.add(new InvoiceFixture(
                    "FAK-2026-" + String.format("%04d", i), issueDate,
                    taxNumber, "11119999", "OUTBOUND",
                    List.of(new LineItemFixture(1, "Irodaszer vegyes", BigDecimal.valueOf(10),
                            "DARAB", BigDecimal.valueOf(2500), BigDecimal.valueOf(25000),
                            BigDecimal.valueOf(27), "48201000"))));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateIncompleteCompanyInvoices(String taxNumber) {
        // Hiányos Bevallású Kft. — missing filings scenario
        // Base: 2026-01-07, step: 2 days → last invoice 2026-01-07 + 29*2 = 2026-03-06 (within Q1)
        List<InvoiceFixture> invoices = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2026, 1, 7);

        for (int i = 1; i <= 30; i++) {
            LocalDate issueDate = baseDate.plusDays((long) (i - 1) * 2);
            String direction = i % 3 == 0 ? "INBOUND" : "OUTBOUND";

            List<LineItemFixture> items = new ArrayList<>();
            items.add(new LineItemFixture(1, "Műanyag cső PE100 Ø110 (6m)", BigDecimal.valueOf(15),
                    "DARAB", BigDecimal.valueOf(8900), BigDecimal.valueOf(133500),
                    BigDecimal.valueOf(27), "39172100"));

            invoices.add(new InvoiceFixture(
                    "HBK-2026-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : "22220000",
                    direction.equals("OUTBOUND") ? "22220000" : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateStartupCompanyInvoices(String taxNumber) {
        // Friss Startup Kft. — young company, fewer invoices
        // Base: 2026-02-01, step: 3 days → last invoice 2026-02-01 + 19*3 = 2026-03-29 (within Q1)
        List<InvoiceFixture> invoices = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2026, 2, 1);

        for (int i = 1; i <= 20; i++) {
            LocalDate issueDate = baseDate.plusDays((long) (i - 1) * 3);
            String direction = i % 4 == 0 ? "INBOUND" : "OUTBOUND";

            List<LineItemFixture> items = new ArrayList<>();
            items.add(new LineItemFixture(1, "SaaS előfizetés (havi)", BigDecimal.ONE,
                    "DARAB", BigDecimal.valueOf(49000), BigDecimal.valueOf(49000),
                    BigDecimal.valueOf(27), "85234000"));
            if (i % 2 == 0) {
                items.add(new LineItemFixture(2, "Cloud hosting (havi)", BigDecimal.ONE,
                        "DARAB", BigDecimal.valueOf(35000), BigDecimal.valueOf(35000),
                        BigDecimal.valueOf(27), "63110000"));
            }

            invoices.add(new InvoiceFixture(
                    "FSK-2026-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : "44440000",
                    direction.equals("OUTBOUND") ? "44440000" : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }
}
