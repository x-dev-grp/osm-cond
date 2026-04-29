package com.osm.conditioning.projet.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.osm.conditioning.projet.enums.TypeClient;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})

@Entity
@Getter
@Setter
public class ProjetClient extends BaseEntity {

    @Column(nullable = false)
    private String nom;

    private String email;

    private String telephone;

    @Enumerated(EnumType.STRING)
    private TypeClient type;

    private String adresse;
}