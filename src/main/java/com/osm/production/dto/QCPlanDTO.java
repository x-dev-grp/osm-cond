package com.osm.production.dto;

import com.xdev.xdevbase.dtos.BaseDto;
import com.osm.production.model.QCPlan;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Data
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class QCPlanDTO extends BaseDto<QCPlan> {
    private UUID ofId;
    private UUID id;
    private String titre;
    private boolean actif;
    private List<QCControlPointDTO> points;  // ou simplement List<UUID> pointIds
}