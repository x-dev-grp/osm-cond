package com.osm.conditioning.expedition.dto;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class IntakeStepDto {
    private String type;
    private UUID deliveryId;
    private UUID transactionId;
    private String deliveryNumber;
    private String lotNumber;
    private String lotOliveNumber;
    private String deliveryType;
    private String supplierName;
    private String deliveryDate;
    private Double quantityKg;
    private UUID storageUnitId;
    private String storageUnitName;
    private Map<String, String> qualityControls;
    private Map<String, Object> extra;
}
