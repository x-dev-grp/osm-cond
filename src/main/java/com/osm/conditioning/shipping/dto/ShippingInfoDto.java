package com.osm.conditioning.shipping.dto;

import com.osm.conditioning.shipping.enums.ShippingStatus;
import com.osm.conditioning.shipping.model.ShippingInfo;
import com.xdev.xdevbase.dtos.BaseDto;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class ShippingInfoDto extends BaseDto<ShippingInfo> {
    private String shippingNumber;
    private UUID projectId;
    private String projectCode;
    private ShippingStatus status;

    private String destination;
    private String incoterm;
    private String carrierName;
    private String driverName;
    private String truckNumber;
    private String trackingNumber;
    private LocalDate expectedShipDate;
    private LocalDateTime departedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime deliveredAt;
    private String notes;

    private String publicCode;
    private String qrImageBase64;

    private List<ShippingLineDto> lines = new ArrayList<>();
    private List<ShippingEventDto> events = new ArrayList<>();
}
