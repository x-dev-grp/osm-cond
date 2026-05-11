package com.osm.conditioning.expedition.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ExpeditionLineCreateRequest {

    private UUID ofId;
    private UUID articleId;
    @Min(1)
    private Integer quantity;
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal volume;
    private String lotNumber;
    private String unit;
}
