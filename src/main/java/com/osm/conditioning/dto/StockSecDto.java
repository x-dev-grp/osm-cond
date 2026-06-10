package com.osm.conditioning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Data
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockSecDto {
    private UUID id;
    private UUID articleId;
    private Integer quantiteActuelle;
    private Integer quantiteReservee;
    private Integer quantiteDisponible;
}
