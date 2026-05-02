package com.osm.conditioning.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Getter
@Setter
public class LigneOFDto {
    private UUID articleId;
    private String articleNom;
    private BigDecimal quantiteTheorique;
    private BigDecimal quantiteReelle;
    private String motifAjustement;
}