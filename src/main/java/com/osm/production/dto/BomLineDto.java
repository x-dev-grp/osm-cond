package com.osm.production.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;
@Data
@Getter
@Setter
public class BomLineDto  {
    private UUID articleId;
    private BigDecimal quantity;

}
