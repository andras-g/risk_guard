package hu.riskguard.epr.api.dto;

import java.time.Instant;

/**
 * Response DTO after publishing a new EPR config version — new version number and activation timestamp.
 */
public record EprConfigPublishResponse(int version, Instant activatedAt) {


}
