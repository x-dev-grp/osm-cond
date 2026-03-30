package com.osm.production.model;


import com.osm.production.Enum.ResultStatus;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
@Data
@Getter
@Setter
@Entity
@Table(name = "qc_result")
public class QCResult extends BaseEntity implements Serializable {

    @ManyToOne
    @JoinColumn(name = "control_point_id", nullable = false)
    private QCControlPoint controlPoint;

    @ManyToOne
    @JoinColumn(name = "of_id", nullable = false)
    private OrdreFabrication of;

    private String valeur;
    @Enumerated(EnumType.STRING)
    private ResultStatus statut;
    private String commentaire;
    private String photo;
    private String signature;
    private LocalDateTime dateControle;



   }