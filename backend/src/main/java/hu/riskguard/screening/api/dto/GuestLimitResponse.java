package hu.riskguard.screening.api.dto;

/**
 * Typed response DTO for guest rate-limit 429 responses.
 * Replaces untyped {@code Map.of()} in GuestSearchController.
 *
 * @param error           limit error code (COMPANY_LIMIT_REACHED or DAILY_LIMIT_REACHED)
 * @param companiesUsed   number of unique companies searched (present for company limit)
 * @param companiesLimit  maximum companies allowed (present for company limit)
 * @param dailyChecksUsed number of daily checks used (present for daily limit)
 * @param dailyChecksLimit maximum daily checks allowed (present for daily limit)
 */
public record GuestLimitResponse(
        String error,
        Integer companiesUsed,
        Integer companiesLimit,
        Integer dailyChecksUsed,
        Integer dailyChecksLimit
) {
    /**
     * General-purpose factory method per ArchUnit DTO convention.
     * Creates a GuestLimitResponse from the given parameters.
     */
    public static GuestLimitResponse from(String error, Integer companiesUsed, Integer companiesLimit,
                                           Integer dailyChecksUsed, Integer dailyChecksLimit) {
        return new GuestLimitResponse(error, companiesUsed, companiesLimit, dailyChecksUsed, dailyChecksLimit);
    }

    /**
     * Factory for company limit reached response.
     */
    public static GuestLimitResponse companyLimitReached(int companiesUsed, int companiesLimit) {
        return from("COMPANY_LIMIT_REACHED", companiesUsed, companiesLimit, null, null);
    }

    /**
     * Factory for daily limit reached response.
     */
    public static GuestLimitResponse dailyLimitReached(int dailyChecksUsed, int dailyChecksLimit) {
        return from("DAILY_LIMIT_REACHED", null, null, dailyChecksUsed, dailyChecksLimit);
    }
}
