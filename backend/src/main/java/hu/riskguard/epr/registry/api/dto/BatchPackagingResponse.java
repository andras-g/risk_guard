package hu.riskguard.epr.registry.api.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/classifier/batch-packaging} (Story 10.3).
 *
 * <p>Contains one {@link BatchPackagingResult} per input pair (in input order)
 * and the post-batch {@link ClassifierUsageInfo} snapshot so the caller can
 * display remaining cap without a follow-up roundtrip (AC #10).
 */
public record BatchPackagingResponse(
        List<BatchPackagingResult> results,
        ClassifierUsageInfo usageInfo
) {
    public static BatchPackagingResponse from(List<BatchPackagingResult> results,
                                              ClassifierUsageInfo usageInfo) {
        return new BatchPackagingResponse(List.copyOf(results), usageInfo);
    }
}
