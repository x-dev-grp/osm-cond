package com.osm.conditioning.projet.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Getter
@Setter
@Audited
public class ProjetReservation extends BaseEntity implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projet_id", nullable = false)
    @JsonIgnore
    private Projet projet;

    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    @Column(name = "quantite_reservee", nullable = false)
    private Double quantiteReservee;
    
    // Status can be e.g. "PENDING", "CONFIRMED", "RELEASED"
    private String statut;
}
