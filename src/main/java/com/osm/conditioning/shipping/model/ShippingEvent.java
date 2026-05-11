package com.osm.conditioning.shipping.model;

import com.osm.conditioning.shipping.enums.ShippingEventType;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Audited
public class ShippingEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_info_id", nullable = false)
    private ShippingInfo shippingInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShippingEventType type;

    private LocalDateTime eventAt;

    @Column(length = 120)
    private String location;

    @Column(length = 1000)
    private String comment;
}

