package com.osm.conditioning.expedition.dto;

import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
public class RootSourceDto {
    private String type;
    private UUID sourceId;
    private String lotNumber;
    private String supplierName;
    private String date;
    private Map<String, Object> extra;
    private Map<String, String> qualityControls;
}
