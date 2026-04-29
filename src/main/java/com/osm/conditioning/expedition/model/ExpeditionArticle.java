package com.osm.conditioning.expedition.model;

import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "expedition_line")
@Audited
@Getter
@Setter
public class ExpeditionArticle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expedition_id", nullable = false)
    private Expedition expedition;

    @Column(name = "of_id")
    private UUID ofId;

    @Column(name = "of_code", length = 50)
    private String ofCode;

    @Column(name = "article_id")
    private UUID articleId;

    @Column(name = "article_name")
    private String articleName;

    @Column(name = "article_name_snapshot", length = 180)
    private String articleNameSnapshot;

    @Column(nullable = false)
    private Integer quantity;

    @Column(precision = 18, scale = 3)
    private BigDecimal volume;

    @Column(name = "lot_number", length = 120)
    private String lotNumber;

    @Column(length = 30)
    private String unit;
}
