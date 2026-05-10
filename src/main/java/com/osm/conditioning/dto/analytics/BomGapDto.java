package com.osm.conditioning.dto.analytics;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BomGapDto {
    private String materialName;
    private BigDecimal plannedQuantity;
    private BigDecimal actualQuantity;
    private BigDecimal gapQuantity;
    private BigDecimal gapPercentage;
}
