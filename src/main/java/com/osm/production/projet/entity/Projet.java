package com.osm.production.projet.entity;

import com.osm.production.projet.enums.TypeEmballage;
import com.osm.production.projet.enums.TypeProduit;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class Projet extends BaseEntity {

    @Column(unique = true, length = 100)
    private String code;//

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projet_client_id", nullable = false)
    private ProjetClient client;

    @Enumerated(EnumType.STRING)
    private TypeProduit typeProduit;

    @Enumerated(EnumType.STRING)
    private TypeEmballage typeEmballage;

    private Double quantiteCible;

    private String unite;

    private LocalDate dateLimiteLivraison;

    private BigDecimal prixUnitaire;

    private BigDecimal valeurTotale;

    @Column(length = 2000)
    private String conditionsLivraison;

    private String statut;
}