package com.osm.conditioning.shipping.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ShippingLineCreateRequest {

    @NotNull
    private UUID articleId;

    @NotNull
    @Min(1)
    private Integer quantity;

    private String unit;
}

