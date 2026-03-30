package com.osm.production.model;


import com.osm.production.Enum.ControlType;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
@Data
@Getter
@Setter
@Entity
@Table(name = "qc_control_point")
public class QCControlPoint extends BaseEntity implements Serializable {

    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    private QCPlan plan;

    private String nom;
    @Enumerated(EnumType.STRING)
    private ControlType type;       // NUMERIC, BOOLEAN, TEXT
    private Double minValue;
    private Double maxValue;
    private boolean blocking;   // si vrai, un résultat NOK bloque l'OF


}