package com.osm.production.model;


import com.osm.production.Enum.StatutOF;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ordre_fabrication")
@Getter
@Setter
public class OrdreFabrication extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutOF statut = StatutOF.BROUILLON;

    private LocalDateTime dateDebutPrevue;
    private LocalDateTime dateFinPrevue;
    private LocalDateTime dateDebutReelle;
    private LocalDateTime dateFinReelle;

    @Column(nullable = false)
    private BigDecimal quantiteCible;

    private BigDecimal quantiteBonne = BigDecimal.ZERO;
    private BigDecimal quantiteNC = BigDecimal.ZERO;
    private Long dureeReelle;
    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "bom_id")
    private UUID bomId;

    @Column(name = "ligne_id")
    private UUID ligneId;

    @Column(name = "lot_vrac_id")
    private UUID lotVracId;

    @OneToMany(mappedBy = "of", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneOF> lignes = new ArrayList<>();
}