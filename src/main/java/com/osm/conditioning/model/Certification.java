package com.osm.conditioning.model;

import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "certification")
@Getter
@Setter
public class Certification extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String code;

    @Column(length = 1000)
    private String description;

    private String issuingBody;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String logoData;

    private String logoContentType;

    private String websiteUrl;

    private String category; // e.g., LEGAL, MARKETING, QUALITY

    private Boolean isActive = true;
}
