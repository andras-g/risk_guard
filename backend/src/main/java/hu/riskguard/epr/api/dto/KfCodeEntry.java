package hu.riskguard.epr.api.dto;

import hu.riskguard.epr.domain.DagEngine;

import java.math.BigDecimal;

/**
 * DTO for a single KF-code entry in the full enumeration list.
 * Used by the manual override autocomplete to display all valid KF-codes.
 *
 * @param kfCode             8-digit KF code (e.g., "11010101")
 * @param feeCode            4-digit díjkód (e.g., "1101")
 * @param feeRate            fee rate in Ft/kg
 * @param currency           currency code ("HUF")
 * @param classification     localized material classification label
 * @param productStreamLabel top-level category label for grouping in UI
 */
public record KfCodeEntry(
        String kfCode,
        String feeCode,
        BigDecimal feeRate,
        String currency,
        String classification,
        String productStreamLabel
) {

    /**
     * Map from DagEngine domain type to API DTO.
     */
    public static KfCodeEntry from(DagEngine.KfCodeEntry entry) {
        return new KfCodeEntry(
                entry.kfCode(),
                entry.feeCode(),
                entry.feeRate(),
                entry.currency(),
                entry.classification(),
                entry.productStreamLabel()
        );
    }
}
