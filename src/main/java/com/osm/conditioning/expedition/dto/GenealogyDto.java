package com.osm.conditioning.expedition.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class GenealogyDto {
    private UUID storageUnitId;
    private String lotNumber;
    private String storageUnitName;
    private List<FiltrationStepDto> filtrations = new ArrayList<>();
    private List<RootSourceDto> rootSources = new ArrayList<>();
}
