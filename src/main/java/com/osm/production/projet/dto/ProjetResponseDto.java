package com.osm.production.projet.dto;

import com.osm.production.projet.entity.Projet;
import com.osm.production.projet.enums.TypeEmballage;
import com.osm.production.projet.enums.TypeProduit;
import com.xdev.xdevbase.dtos.BaseDto;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class ProjetResponseDto extends BaseDto<Projet> {
    private UUID id;
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
    private LocalDateTime createdDate;

    // QR code fields
    private String qrCode;
    private String qrImageBase64;
}