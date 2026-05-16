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
public class ProjetProduit extends BaseEntity implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projet_id", nullable = false)
    @JsonIgnore
    private Projet projet;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "bom_id", nullable = false)
    private UUID bomId;

    @Column(name = "quantite_cible", nullable = false)
    private Double quantiteCible;
}
