package com.osm.conditioning.dto.analytics;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FiltrationReportDto {
    private String operationId;
    private LocalDateTime operationDate;
    private BigDecimal inputVolume;
    private BigDecimal outputVolume;
    private BigDecimal lossVolume;
    private BigDecimal efficiencyRate;
}
