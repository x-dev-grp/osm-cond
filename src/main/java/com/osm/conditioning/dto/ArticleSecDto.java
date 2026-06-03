package com.osm.conditioning.dto;

import com.xdev.communicator.models.common.dtos.BaseDto;
import com.xdev.xdevbase.models.UniteMesure;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Data
@Getter
@Setter
public class ArticleSecDto extends BaseDto {
    private UUID id;
    private String nom;
    private UniteMesure um;
    private String categorie;
    private Map<String, Object> configuration;
}
