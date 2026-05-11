package com.osm.conditioning.model;

import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Getter
@Audited
@Setter
@Entity
@Table(name = "qc_plan")
public class QCPlan extends BaseEntity implements Serializable {

    @ManyToOne
    @JoinColumn(name = "of_id", nullable = false)
    private OrdreFabrication of;

    private String titre;
    private boolean actif = true;
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QCControlPoint> points = new ArrayList<>();}