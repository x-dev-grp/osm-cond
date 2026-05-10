package com.osm.conditioning.dto.analytics;

import com.osm.conditioning.Enum.StatutOF;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projection to fetch only necessary fields for analytics, avoiding LOB loading issues in PostgreSQL.
 */
public interface OfAnalyticsProjection {
    UUID getId();
    String getCode();
    StatutOF getStatut();
    BigDecimal getQuantiteCible();
    BigDecimal getQuantiteBonne();
    LocalDateTime getDateDebutPrevue();
    UUID getSkuId();
    UUID getBomId();
}
