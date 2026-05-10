package com.osm.conditioning.dto.analytics;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class OfYieldDto {
    private String ofCode;
    private String statut;
    private UUID skuId;
    private BigDecimal quantiteCible;
    private BigDecimal quantiteBonne;
    private BigDecimal yieldPercentage;
}
