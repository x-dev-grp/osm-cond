package com.osm.conditioning.shipping.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ShippingInfoUpsertRequest {
    private String destination;
    private String incoterm;
    private String carrierName;
    private String driverName;
    private String truckNumber;
    private String trackingNumber;
    private LocalDate expectedShipDate;
    private String notes;
}

