package com.osm.conditioning.expedition.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ExpeditionCreationRequest {

    @NotNull
    private UUID projetId;

    private String destination;
    private LocalDate plannedShipDate;
    private String notes;
}
