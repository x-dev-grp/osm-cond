package com.osm.production.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
@Data
@Getter
@Setter
public class LigneConditionnementDto {
    private UUID id;
    private String code;
    private String nom;
}
