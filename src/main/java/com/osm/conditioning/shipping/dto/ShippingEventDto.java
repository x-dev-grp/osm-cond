package com.osm.conditioning.shipping.dto;

import com.osm.conditioning.shipping.enums.ShippingEventType;
import com.osm.conditioning.shipping.model.ShippingEvent;
import com.xdev.xdevbase.dtos.BaseDto;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShippingEventDto extends BaseDto<ShippingEvent> {
    private ShippingEventType type;
    private LocalDateTime eventAt;
    private String location;
    private String comment;
}
