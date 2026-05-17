package com.osm.conditioning.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xdev.communicator.models.common.dtos.BaseDto;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Data
@Getter
@Setter
public class ProduitFinalDto extends BaseDto {
    private UUID id;
    private String name;
    @JsonAlias("skuCode")
    private String code;
    private ProductType type;
    private String category;
    private String unitOfMeasure;
    private String description;
    private String grade;
    private String origin;
    private String harvestCampaign;
    private Float volume;
    private String packagingType;
    private String barcode;
    @JsonAlias("unitesParCols")
    private Integer unitsPerCarton;
    @JsonAlias("colisParPalette")
    private Integer cartonsPerPallet;
    private Float netWeight;
    private Float grossWeight;
    private String brand;
    private Float density;
    private String storageUnit;
    private Boolean actif;

    @JsonProperty("unitesParCols")
    public Integer getUnitesParCols() {
        return unitsPerCarton;
    }

    public void setUnitesParCols(Integer unitesParCols) {
        this.unitsPerCarton = unitesParCols;
    }

    @JsonProperty("colisParPalette")
    public Integer getColisParPalette() {
        return cartonsPerPallet;
    }

    public void setColisParPalette(Integer colisParPalette) {
        this.cartonsPerPallet = colisParPalette;
    }
}
