package com.osm.production.dto;


import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Data
@Getter
@Setter

public class StockSecDto {
    private UUID id;
    private UUID articleId;
    private Integer quantiteActuelle;
    // getters / setters
}