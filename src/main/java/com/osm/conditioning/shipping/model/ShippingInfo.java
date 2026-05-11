package com.osm.conditioning.shipping.model;

import com.osm.conditioning.projet.entity.Projet;
import com.osm.conditioning.shipping.enums.ShippingStatus;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Audited
public class ShippingInfo extends BaseEntity {

    @Column(name = "shipping_number", nullable = false, unique = true, length = 80)
    private String shippingNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projet_id", nullable = false, unique = true)
    private Projet projet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShippingStatus status = ShippingStatus.DRAFT;

    @Column(length = 255)
    private String destination;

    @Column(length = 30)
    private String incoterm;

    private String carrierName;

    private String driverName;

    private String truckNumber;

    private String trackingNumber;

    private LocalDate expectedShipDate;

    private LocalDateTime departedAt;

    private LocalDateTime arrivedAt;

    private LocalDateTime deliveredAt;

    @Column(length = 2000)
    private String notes;

    @OneToMany(mappedBy = "shippingInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShippingLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "shippingInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShippingEvent> events = new ArrayList<>();
}

