package com.osm.production.dto;

import com.xdev.xdevbase.models.UniteMesure;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
@Data
@Getter
@Setter

public class ArticleSecDto {
    private UUID id;
    private String nom;
    private UniteMesure um;
}
