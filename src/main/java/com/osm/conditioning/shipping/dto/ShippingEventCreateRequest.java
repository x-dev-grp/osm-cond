package com.osm.conditioning.shipping.dto;

import com.osm.conditioning.shipping.enums.ShippingEventType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShippingEventCreateRequest {

    @NotNull
    private ShippingEventType type;

    private String location;
    private String comment;
}

