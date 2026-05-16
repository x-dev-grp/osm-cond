package com.osm.conditioning.projet.dto;

import com.xdev.xdevbase.dtos.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjetProduitDto extends BaseDto<com.osm.conditioning.projet.entity.ProjetProduit> {
    private UUID projetId;
    private UUID productId;
    private UUID bomId;
    private Double quantiteCible;
}
