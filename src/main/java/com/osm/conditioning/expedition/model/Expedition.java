package com.osm.conditioning.expedition.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.osm.conditioning.expedition.enums.ExpeditionStatus;
import com.osm.conditioning.projet.entity.Projet;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "expedition")
@Audited
@Getter
@Setter
public class Expedition extends BaseEntity {

    @Column(name = "expedition_number", unique = true, nullable = false, length = 80)
    private String expeditionNumber;

    @ManyToOne
    @JoinColumn(name = "projet_id", nullable = false)
    @Audited(targetAuditMode = NOT_AUDITED)
    private Projet projet;

    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExpeditionStatus status = ExpeditionStatus.DRAFT;

    @Column(length = 255)
    private String destination;

    private LocalDate plannedShipDate;

    private LocalDateTime validatedAt;

    private LocalDateTime shippedAt;

    private LocalDateTime closedAt;

    private LocalDateTime cancelledAt;

    @Column(length = 2000)
    private String notes;
    private Integer totalQuantity;
    private BigDecimal totalVolume;

    /* Transport / shipping fields merged from ShippingInfo. */

    @Column(length = 120)
    private String carrierName;

    @Column(length = 120)
    private String driverName;

    @Column(length = 60)
    private String truckNumber;

    @Column(length = 120)
    private String trackingNumber;

    @Column(length = 30)
    private String incoterm;

    @Column(name = "traceability_snapshot_json", columnDefinition = "TEXT")
    private String traceabilitySnapshotJson;

    private LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "expedition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpeditionArticle> lines = new ArrayList<>();
}
