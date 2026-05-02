package com.osm.conditioning.shipping.dto;

import com.osm.conditioning.shipping.model.ShippingLine;
import com.xdev.xdevbase.dtos.BaseDto;
import lombok.Data;

import java.util.UUID;

@Data
public class ShippingLineDto extends BaseDto<ShippingLine> {
    private UUID articleId;
    private String articleName;
    private Integer quantity;
    private String unit;
}
