package com.osm.conditioning.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class MaterialNeedLineDto {
    private UUID articleId;
    private String articleName;
    private String unitOfMeasure;
    private double quantityPerUnit;
    private double quantityNeeded;
    private int quantityNeededRounded;
    private Integer quantiteActuelle;
    private Integer quantiteReservee;
    private Integer quantiteDisponible;
    private boolean sufficient;
}
