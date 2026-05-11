package com.osm.conditioning.model;


import com.osm.conditioning.Enum.ControlType;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serializable;
@Data
@Getter
@Setter
@Entity
@Audited
@Table(name = "qc_control_point")
public class QCControlPoint extends BaseEntity implements Serializable {

    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    private QCPlan plan;
    private String nom;
    @Enumerated(EnumType.STRING)
    private ControlType type;
    private Double minValue;
    private Double maxValue;
    private boolean blocking;


}