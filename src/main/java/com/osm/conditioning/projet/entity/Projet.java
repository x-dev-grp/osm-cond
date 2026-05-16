package com.osm.conditioning.projet.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.osm.conditioning.model.OrdreFabrication;
import com.osm.conditioning.projet.enums.TypeEmballage;
import com.osm.conditioning.projet.enums.TypeProduit;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Getter
@Audited
@Setter
public class Projet extends BaseEntity {

    @Column(unique = true, length = 100)
    private String code;

    @OneToMany(mappedBy = "projet", cascade = CascadeType.ALL)
    private List<OrdreFabrication> ordresFabrication = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

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

    @OneToMany(mappedBy = "projet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjetProduit> produits = new ArrayList<>();

    @OneToMany(mappedBy = "projet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjetReservation> reservations = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "projet_lignes_conditionnement", joinColumns = @JoinColumn(name = "projet_id"))
    @Column(name = "ligne_id", nullable = false)
    private List<UUID> ligneIds = new ArrayList<>();
}
