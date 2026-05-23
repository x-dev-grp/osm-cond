package com.osm.conditioning.expedition.dto;

import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
public class FiltrationStepDto {
    private UUID operationId;
    private String sourceLotNumber;
    private String targetLotNumber;
    private Double volumeFiltered;
    private String timestamp;
    
    private UUID sourceStorageUnitId;
    private String sourceStorageUnitName;
    private Map<String, String> qualityControls;
    private java.util.List<IntakeStepDto> sourceIntakeChain = new java.util.ArrayList<>();
}
