package com.osm.production.dto;



import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Data
@Getter
@Setter
public class SaisieProductionDto  {
    private BigDecimal quantiteBonne;
    private BigDecimal quantiteNC;
}