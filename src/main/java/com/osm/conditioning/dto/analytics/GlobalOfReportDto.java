package com.osm.conditioning.dto.analytics;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class GlobalOfReportDto {
    private long totalOf;
    private long plannedOf;
    private long inProgressOf;
    private long completedOf;
    private long canceledOf;
    private BigDecimal totalTargetQuantity = BigDecimal.ZERO;
    private BigDecimal totalProducedQuantity = BigDecimal.ZERO;
}
