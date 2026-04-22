package com.osm.production.projet.entity;

import com.osm.production.projet.enums.TypeClient;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

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