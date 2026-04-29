package com.osm.conditioning.expedition.dto;

import com.osm.conditioning.expedition.enums.ExpeditionStatus;
import com.osm.conditioning.expedition.model.Expedition;
import com.xdev.xdevbase.dtos.BaseDto;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class ExpeditionDto extends BaseDto<Expedition> {
    private String expeditionNumber;
    private UUID projetId;
    private String projetCode;
    private UUID clientId;

    private ExpeditionStatus status;
    private String destination;
    private LocalDate plannedShipDate;
    private LocalDateTime validatedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime closedAt;
    private LocalDateTime cancelledAt;
    private String notes;

    /* Transport fields merged from shipping. */
    private String carrierName;
    private String driverName;
    private String truckNumber;
    private String trackingNumber;
    private String incoterm;
    private LocalDateTime deliveredAt;
    private String traceabilitySnapshotJson;

    private Integer totalQuantity;
    private BigDecimal totalVolume;

    private String publicCode;
    private String qrImageBase64;

    private List<ExpeditionArticleDto> lines = new ArrayList<>();
}
