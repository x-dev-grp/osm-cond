package com.osm.conditioning.projet.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.osm.conditioning.model.OrdreFabrication;
import com.osm.conditioning.projet.entity.Projet;
import com.osm.conditioning.projet.entity.ProjetClient;
import com.osm.conditioning.projet.enums.TypeEmballage;
import com.osm.conditioning.projet.enums.TypeProduit;
import com.xdev.xdevbase.dtos.BaseDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjetDto extends BaseDto<Projet> {

    private UUID id;

    private String code;

    @NotNull(message = "Le client est obligatoire")
    private UUID clientId;

    private String clientNom;
    private String clientEmail;

    private TypeProduit typeProduit;

    private TypeEmballage typeEmballage;

    private Double quantiteCible;

    private String unite;

    private LocalDate dateLimiteLivraison;

    private BigDecimal prixUnitaire;

    private BigDecimal valeurTotale;

    private String conditionsLivraison;

    private String statut;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdDate;

    private String publicCode;
    private String qrUrl;
    private String qrImageBase64;
    private Double tauxAvancement;
    private Double quantiteProduite;
    private Integer nombreOF;

    private UUID skuId;
    private String skuCode;
    private UUID bomId;
}