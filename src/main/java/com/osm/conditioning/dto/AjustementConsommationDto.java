package com.osm.conditioning.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AjustementConsommationDto {
    private UUID articleId;
    private BigDecimal quantiteReelle;
    private String motif;
}