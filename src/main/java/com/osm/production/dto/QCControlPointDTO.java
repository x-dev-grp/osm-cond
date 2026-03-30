package com.osm.production.dto;


import com.osm.production.Enum.ControlType;
import com.xdev.xdevbase.dtos.BaseDto;
import com.osm.production.model.QCControlPoint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Data
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class QCControlPointDTO extends BaseDto<QCControlPoint> {
    private UUID planId;
    private String nom;
    private ControlType type;
    private Double minValue;
    private Double maxValue;
    private boolean blocking;
}