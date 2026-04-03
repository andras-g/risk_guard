package hu.riskguard.datasource.domain;

import java.util.List;

/**
 * Result wrapper for invoice queries that carries both the data and service availability status.
 * Distinguishes "NAV returned empty results" (serviceAvailable=true, summaries empty)
 * from "NAV is down / error occurred" (serviceAvailable=false, summaries empty).
 *
 * @param summaries        list of invoice summaries (empty on error or no results)
 * @param serviceAvailable true if the data source responded successfully, false if an error occurred
 */
public record InvoiceQueryResult(List<InvoiceSummary> summaries, boolean serviceAvailable) {}
