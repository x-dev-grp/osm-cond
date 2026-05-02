package com.osm.conditioning.expedition.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExpeditionUpdateRequest {
    private String destination;
    private LocalDate plannedShipDate;
    private String notes;

    /* Transport */
    private String carrierName;
    private String driverName;
    private String truckNumber;
    private String trackingNumber;
    private String incoterm;
}
