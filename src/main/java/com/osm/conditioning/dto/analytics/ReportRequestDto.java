package com.osm.conditioning.dto.analytics;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ReportRequestDto {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private UUID productId;
    private UUID ofId;
    private String status;
    private String reportType; // "YIELD", "GLOBAL", "QUALITY", "BOM", "FILTRATION"
}
