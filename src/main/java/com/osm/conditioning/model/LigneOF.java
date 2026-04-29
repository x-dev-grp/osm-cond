package com.osm.conditioning.model;

import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ligne_of")
@Getter
@Setter
public class LigneOF extends BaseEntity implements Serializable {

    @ManyToOne
    @JoinColumn(name = "of_id", nullable = false)
    private OrdreFabrication of;

    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    @Column(nullable = false)
    private BigDecimal quantiteTheorique;

    private BigDecimal quantiteReelle;

    private String motifAjustement;
}