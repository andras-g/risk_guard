package hu.riskguard.epr.api.dto;

import java.util.List;

public record EprSubmissionPage(
        List<EprSubmissionSummary> content,
        long totalElements,
        int page,
        int size
) {}
