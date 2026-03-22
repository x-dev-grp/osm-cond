package com.osm.production.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
@Data
@Getter
@Setter
public class SKUDto {
    private UUID id;
    private String code;
    private Float volume;
    private String category;
    private Integer unitesParCols;
    private Integer colisParPalette;
    private Boolean actif;
}
