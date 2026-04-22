package hu.riskguard.datasource.internal.adapters.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo invoice fixtures providing realistic Hungarian invoice data for EPR auto-fill.
 * Each fixture company has 20-50 invoices for the <em>previous quarter</em> relative to
 * the current date. This ensures the invoice auto-fill panel always shows results when
 * the date range is defaulted to the previous (most recently completed) quarter.
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

    /** Map of 8-digit tax number → list of invoices for the previous quarter. */
    private static final Map<String, List<InvoiceFixture>> INVOICES = new LinkedHashMap<>();

    static {
        INVOICES.put("12345678", generateTradeCompanyInvoices("12345678"));
        INVOICES.put("99887766", generateFoodCompanyInvoices("99887766"));
        INVOICES.put("11223344", generateServiceCompanyInvoices("11223344"));
        INVOICES.put("55667788", generateFurnitureCompanyInvoices("55667788"));
        INVOICES.put("44556677", generateMixedCompanyInvoices("44556677"));
        INVOICES.put("33445566", generateSuspendedCompanyInvoices("33445566"));
        INVOICES.put("77889900", generateIncompleteCompanyInvoices("77889900"));
        INVOICES.put("22334455", generateStartupCompanyInvoices("22334455"));
    }

    /**
     * Returns the first day of the previous quarter relative to today.
     * EPR filings cover the most recently completed quarter.
     */
    static LocalDate previousQuarterStart() {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue(); // 1-12
        int currentQ = (month - 1) / 3;   // 0-3
        int prevQ = currentQ == 0 ? 3 : currentQ - 1;
        int year = currentQ == 0 ? today.getYear() - 1 : today.getYear();
        return LocalDate.of(year, Month.of(prevQ * 3 + 1), 1);
    }

    /**
     * Returns the last day of the previous quarter relative to today.
     */
    static LocalDate previousQuarterEnd() {
        return previousQuarterStart().plusMonths(3).minusDays(1);
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
        LocalDate baseDate = previousQuarterStart().plusDays(4);

        for (int i = 1; i <= 35; i++) {
            LocalDate issueDate = baseDate.plusDays((long) i * 2);
            if (issueDate.isAfter(previousQuarterEnd())) break;
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
                    "PKK-" + previousQuarterStart().getYear() + "-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : counterparty,
                    direction.equals("OUTBOUND") ? counterparty : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }

    /** Product pool entry used by the food/furniture generators below.
     *  {@code articleNumber} is the registry cross-reference but is not serialised onto
     *  invoice line items — NAV invoice lines carry description + VTSZ, and the
     *  {@code RegistryLookupService} matches via those. Kept here for documentation. */
    private record ProductPoolEntry(
            String articleNumber,
            String description,
            String vtszCode,
            String unit,
            int baseQuantity,
            int unitPriceHuf,
            int vatRatePct
    ) {}

    /**
     * Zöld Élelmiszer Kft. (tax 99887766) — organic/green food producer.
     * Retail-scale invoices: ~7000/quarter = 1 invoice / 5 min × 8 h × 6 days × 12 weeks.
     * Each invoice is one retail customer's basket — 5–10 line items, 1–3 kg total.
     * Seed-data registry (R__demo_data.sql Section 15) carries 15 food products;
     * 3 (olive oil, coffee, tea) are intentionally absent from invoices to
     * represent unsold catalog inventory.
     */
    private static List<InvoiceFixture> generateFoodCompanyInvoices(String taxNumber) {
        // Product pool — matches article_numbers and VTSZ codes in R__demo_data.sql Section 15.
        // Unsold products (OLIVA-EV-500ML, KAVE-P-250G, TEA-F-50G) are intentionally excluded.
        // baseQuantity reflects a typical retail customer buying 1 piece (most SKUs),
        // 2 for multipack items (water, yogurt). Total basket lands around 2–3 kg.
        //
        // Story 10.11 epr_scope tagging for the registered products:
        //   FIRST_PLACER (10): KEN-F-500G, LISZT-BL55-1KG, RIZS-HB-1KG, TESZTA-O-500G, MEZ-A-500G,
        //                      DZS-E-300G, OLAJ-N-1L, VIZ-SZM-15L, CUKOR-K-1KG, YOG-N-150G
        //   RESELLER (4):      OLIVA-EV-500ML, KAVE-P-250G, TEA-F-50G, LE-N-1L
        //   UNKNOWN (1):       LE-A-1L
        // The pool below includes LE-N-1L (RESELLER) so the aggregation test can assert that the
        // repo-layer RESELLER filter drops it even though invoices reference it.
        ProductPoolEntry[] pool = new ProductPoolEntry[] {
                new ProductPoolEntry("KEN-F-500G",     "Kenyér fehér (500g)",             "19059090", "DARAB", 1, 320,  5),
                new ProductPoolEntry("LISZT-BL55-1KG", "Búzaliszt BL-55 (1kg)",           "11010015", "DARAB", 1, 280,  18),
                new ProductPoolEntry("RIZS-HB-1KG",    "Hosszúszemű rizs \"A\" (1kg)",    "10063099", "DARAB", 1, 680,  18),
                new ProductPoolEntry("TESZTA-O-500G",  "Durum tészta orsó (500g)",        "19021910", "DARAB", 1, 520,  18),
                new ProductPoolEntry("MEZ-A-500G",     "Akácméz (500g)",                  "04090000", "DARAB", 1, 2400, 5),
                new ProductPoolEntry("DZS-E-300G",     "Eperlekvár (300g)",               "20079100", "DARAB", 1, 890,  18),
                new ProductPoolEntry("OLAJ-N-1L",      "Napraforgó étolaj (1L)",          "15121999", "DARAB", 1, 980,  18),
                new ProductPoolEntry("LE-A-1L",        "Almalé 100% (1L)",                "20097990", "DARAB", 1, 520,  18),
                new ProductPoolEntry("LE-N-1L",        "Narancslé 100% (1L)",             "20091990", "DARAB", 1, 580,  18),
                new ProductPoolEntry("VIZ-SZM-15L",    "Ásványvíz szénsavmentes (1,5L)",  "22011000", "DARAB", 1, 180,  27),
                new ProductPoolEntry("CUKOR-K-1KG",    "Kristálycukor (1kg)",             "17019910", "DARAB", 1, 320,  5),
                new ProductPoolEntry("YOG-N-150G",     "Natúr joghurt (150g)",            "04031011", "DARAB", 2, 125,  5),
        };
        return generatePoolInvoices(taxNumber, "ZEL", 7000, "77778888", pool, /*alternateDirection*/ 4);
    }

    /**
     * Prémium Bútor Zrt. (tax 55667788) — premium furniture manufacturer.
     * Generates ~42 invoices over the previous quarter with 3–7 line items per
     * invoice drawn deterministically from a 12-product pool.
     * Seed-data registry (R__demo_data.sql Section 16) carries 15 furniture products;
     * 3 (office chair, TV stand, puff) are intentionally absent from invoices.
     */
    private static List<InvoiceFixture> generateFurnitureCompanyInvoices(String taxNumber) {
        // Product pool — matches article_numbers and VTSZ codes in R__demo_data.sql Section 16.
        // Unsold products (SZEK-IR-01, KON-TV-01, PUF-T-01) are intentionally excluded.
        //
        // Story 10.11 epr_scope tagging for the registered products:
        //   FIRST_PLACER (12): SZEK-ET-01, SZEK-IR-01, FOT-SZ-01, KAN-3-B-01, ASZ-ET-160,
        //                      ASZ-DOH-01, ASZ-IR-140, SZK-4A-01, AGY-K-160, EJJ-01, FIO-K-4, KON-PL-5
        //   RESELLER (2):      MAT-H-160, PUF-T-01
        //   UNKNOWN (1):       KON-TV-01
        // The pool below still references MAT-H-160 (RESELLER) so the aggregation test can assert
        // the scope filter drops it.
        ProductPoolEntry[] pool = new ProductPoolEntry[] {
                new ProductPoolEntry("SZEK-ET-01",  "Étkezőszék tömör bükk",               "94016900", "DARAB", 12, 28500,  27),
                new ProductPoolEntry("FOT-SZ-01",   "Fotel kárpitos szövet",               "94016100", "DARAB", 4,  89000,  27),
                new ProductPoolEntry("KAN-3-B-01",  "Kanapé 3 személyes bőr",              "94016100", "DARAB", 2,  345000, 27),
                new ProductPoolEntry("ASZ-ET-160",  "Étkezőasztal 160x90",                 "94036010", "DARAB", 4,  145000, 27),
                new ProductPoolEntry("ASZ-DOH-01",  "Dohányzóasztal",                      "94036090", "DARAB", 8,  68000,  27),
                new ProductPoolEntry("ASZ-IR-140",  "Íróasztal 140x70",                    "94033011", "DARAB", 6,  95000,  27),
                new ProductPoolEntry("SZK-4A-01",   "Szekrény 4 ajtós",                    "94035091", "DARAB", 3,  245000, 27),
                new ProductPoolEntry("AGY-K-160",   "Ágykeret 160x200",                    "94015000", "DARAB", 4,  185000, 27),
                new ProductPoolEntry("MAT-H-160",   "Matrac habosított 160x200",           "94042910", "DARAB", 4,  125000, 27),
                new ProductPoolEntry("EJJ-01",      "Éjjeliszekrény 2 fiókos",             "94035091", "DARAB", 10, 38000,  27),
                new ProductPoolEntry("FIO-K-4",     "Fiókos komód 4 fiókos",               "94035091", "DARAB", 5,  115000, 27),
                new ProductPoolEntry("KON-PL-5",    "Könyvespolc 5 polcos",                "94036010", "DARAB", 6,  58000,  27),
        };
        return generatePoolInvoices(taxNumber, "BUT", 42, "33334444", pool, /*alternateDirection*/ 4);
    }

    /**
     * Generates {@code targetCount} invoices distributed evenly across the previous quarter.
     * Each invoice draws 5–10 line items deterministically from {@code pool} (no randomness —
     * pure {@code i}/{@code j} arithmetic — so every startup produces identical fixtures).
     *
     * @param taxNumber          8-digit tax number of the generating company
     * @param numberPrefix       invoice-number prefix (e.g., "ZEL", "BUT")
     * @param targetCount        number of invoices to generate
     * @param counterpartyTax    8-digit tax number used as counterparty
     * @param pool               product pool to draw line items from
     * @param inboundEveryN      every Nth invoice is INBOUND; rest are OUTBOUND
     */
    private static List<InvoiceFixture> generatePoolInvoices(
            String taxNumber, String numberPrefix, int targetCount,
            String counterpartyTax, ProductPoolEntry[] pool, int inboundEveryN) {
        List<InvoiceFixture> invoices = new ArrayList<>(targetCount);
        LocalDate qStart = previousQuarterStart();
        LocalDate qEnd = previousQuarterEnd();
        long maxDayOffset = Math.max(0L, ChronoUnit.DAYS.between(qStart, qEnd));
        int year = qStart.getYear();
        int digits = targetCount >= 10000 ? 5 : 4;
        String numberFormat = "%0" + digits + "d";

        for (int i = 1; i <= targetCount; i++) {
            long dayOffset = targetCount <= 1
                    ? 0L
                    : ((long) (i - 1) * maxDayOffset) / (targetCount - 1);
            LocalDate issueDate = qStart.plusDays(dayOffset);
            if (issueDate.isAfter(qEnd)) issueDate = qEnd;

            String direction = i % inboundEveryN == 0 ? "INBOUND" : "OUTBOUND";
            int lineCount = 3 + (i % 5); // 3..7 line items (retail basket)
            List<LineItemFixture> items = new ArrayList<>(lineCount);
            for (int j = 0; j < lineCount; j++) {
                int poolIdx = Math.floorMod(i * 7 + j * 3, pool.length);
                ProductPoolEntry p = pool[poolIdx];
                BigDecimal qty = BigDecimal.valueOf(
                        Math.max(1L, Math.round(p.baseQuantity() * (0.7 + ((i + j) % 6) * 0.1))));
                BigDecimal unitPrice = BigDecimal.valueOf(p.unitPriceHuf());
                BigDecimal netAmount = qty.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP);
                items.add(new LineItemFixture(
                        j + 1, p.description(), qty, p.unit(), unitPrice, netAmount,
                        BigDecimal.valueOf(p.vatRatePct()), p.vtszCode()));
            }

            invoices.add(new InvoiceFixture(
                    numberPrefix + "-" + year + "-" + String.format(numberFormat, i),
                    issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : counterpartyTax,
                    direction.equals("OUTBOUND") ? counterpartyTax : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateServiceCompanyInvoices(String taxNumber) {
        // Adós Szolgáltató Bt. — IT services
        List<InvoiceFixture> invoices = new ArrayList<>();
        LocalDate baseDate = previousQuarterStart().plusDays(7);

        for (int i = 1; i <= 22; i++) {
            LocalDate issueDate = baseDate.plusDays((long) (i - 1) * 3);
            if (issueDate.isAfter(previousQuarterEnd())) break;
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
                    "ASB-" + previousQuarterStart().getYear() + "-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : "55556666",
                    direction.equals("OUTBOUND") ? "55556666" : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateMixedCompanyInvoices(String taxNumber) {
        // Hátralékos és Csődös Kft. — mixed goods
        List<InvoiceFixture> invoices = new ArrayList<>();
        LocalDate baseDate = previousQuarterStart().plusDays(9);

        for (int i = 1; i <= 25; i++) {
            LocalDate issueDate = baseDate.plusDays((long) i * 3);
            if (issueDate.isAfter(previousQuarterEnd())) break;
            String direction = i <= 15 ? "OUTBOUND" : "INBOUND";

            List<LineItemFixture> items = new ArrayList<>();
            items.add(new LineItemFixture(1, "Alumínium profil 40x40 (3m)", BigDecimal.valueOf(30),
                    "DARAB", BigDecimal.valueOf(4200), BigDecimal.valueOf(126000),
                    BigDecimal.valueOf(27), "76042100"));

            invoices.add(new InvoiceFixture(
                    "HCK-" + previousQuarterStart().getYear() + "-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : "99990000",
                    direction.equals("OUTBOUND") ? "99990000" : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateSuspendedCompanyInvoices(String taxNumber) {
        // Felfüggesztett Adószámú Kft. — minimal invoices (suspended)
        List<InvoiceFixture> invoices = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            LocalDate issueDate = previousQuarterStart().plusDays((long) i * 5);
            if (issueDate.isAfter(previousQuarterEnd())) break;
            invoices.add(new InvoiceFixture(
                    "FAK-" + previousQuarterStart().getYear() + "-" + String.format("%04d", i), issueDate,
                    taxNumber, "11119999", "OUTBOUND",
                    List.of(new LineItemFixture(1, "Irodaszer vegyes", BigDecimal.valueOf(10),
                            "DARAB", BigDecimal.valueOf(2500), BigDecimal.valueOf(25000),
                            BigDecimal.valueOf(27), "48201000"))));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateIncompleteCompanyInvoices(String taxNumber) {
        // Hiányos Bevallású Kft. — missing filings scenario
        List<InvoiceFixture> invoices = new ArrayList<>();
        LocalDate baseDate = previousQuarterStart().plusDays(6);

        for (int i = 1; i <= 30; i++) {
            LocalDate issueDate = baseDate.plusDays((long) (i - 1) * 2);
            if (issueDate.isAfter(previousQuarterEnd())) break;
            String direction = i % 3 == 0 ? "INBOUND" : "OUTBOUND";

            List<LineItemFixture> items = new ArrayList<>();
            items.add(new LineItemFixture(1, "Műanyag cső PE100 Ø110 (6m)", BigDecimal.valueOf(15),
                    "DARAB", BigDecimal.valueOf(8900), BigDecimal.valueOf(133500),
                    BigDecimal.valueOf(27), "39172100"));

            invoices.add(new InvoiceFixture(
                    "HBK-" + previousQuarterStart().getYear() + "-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : "22220000",
                    direction.equals("OUTBOUND") ? "22220000" : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }

    private static List<InvoiceFixture> generateStartupCompanyInvoices(String taxNumber) {
        // Friss Startup Kft. — young company, fewer invoices
        List<InvoiceFixture> invoices = new ArrayList<>();
        LocalDate baseDate = previousQuarterStart().plusDays(30);

        for (int i = 1; i <= 20; i++) {
            LocalDate issueDate = baseDate.plusDays((long) (i - 1) * 3);
            if (issueDate.isAfter(previousQuarterEnd())) break;
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
                    "FSK-" + previousQuarterStart().getYear() + "-" + String.format("%04d", i), issueDate,
                    direction.equals("OUTBOUND") ? taxNumber : "44440000",
                    direction.equals("OUTBOUND") ? "44440000" : taxNumber,
                    direction, List.copyOf(items)));
        }
        return List.copyOf(invoices);
    }
}
