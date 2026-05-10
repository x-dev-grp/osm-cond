package com.osm.conditioning.dto.analytics;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class QualityReportDto {
    private String productName;
    private long totalControls;
    private long failedControls;
    private BigDecimal nonConformityRate;
}
