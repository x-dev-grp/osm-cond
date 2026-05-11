package com.osm.conditioning.model;


import com.osm.conditioning.Enum.ResultStatus;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import org.hibernate.envers.Audited;

@Data
@Getter
@Setter
@Entity
@Table(name = "qc_result")
@Audited
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
    @Column(columnDefinition = "TEXT")
    private String photo;
    private String signature;
    private LocalDateTime dateControle;



   }