package hu.riskguard.epr.api.dto;

import org.jooq.JSONB;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Response DTO for the active EPR config — version, raw JSON data, and activation timestamp.
 */
public record EprConfigResponse(int version, String configData, Instant activatedAt) {

    public static EprConfigResponse from(org.jooq.Record r) {
        Object raw = r.get("config_data");
        String configJson = (raw instanceof JSONB jsonb) ? jsonb.data() : raw.toString();
        OffsetDateTime activatedAt = r.get("activated_at", OffsetDateTime.class);
        return new EprConfigResponse(
                r.get("version", Integer.class),
                configJson,
                activatedAt != null ? activatedAt.toInstant() : null
        );
    }
}
