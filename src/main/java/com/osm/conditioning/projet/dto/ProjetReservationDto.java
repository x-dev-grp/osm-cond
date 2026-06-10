package com.osm.conditioning.projet.dto;

import com.osm.conditioning.projet.entity.ProjetReservation;
import com.xdev.xdevbase.dtos.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjetReservationDto extends BaseDto<ProjetReservation> {
    private UUID projetId;
    private UUID articleId;
    private Double quantiteReservee;
    private String statut;
}
