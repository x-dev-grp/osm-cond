package com.osm.production.projet.dto;

import com.osm.production.projet.entity.Projet;
import com.osm.production.projet.enums.TypeEmballage;
import com.osm.production.projet.enums.TypeProduit;
import com.xdev.xdevbase.dtos.BaseDto;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @NotNull(message = "Le type de produit est obligatoire")
    private TypeProduit typeProduit;

    @NotNull(message = "Le type d'emballage est obligatoire")
    private TypeEmballage typeEmballage;

    @NotNull(message = "La quantité est obligatoire")
    @Positive(message = "La quantité doit être positive")
    private Double quantiteCible;

    @NotBlank(message = "L'unité est obligatoire")
    private String unite;

    @NotNull(message = "La date limite est obligatoire")
    @FutureOrPresent(message = "La date doit être aujourd'hui ou dans le futur")
    private LocalDate dateLimiteLivraison;

    @NotNull(message = "Le prix unitaire est obligatoire")
    @Positive(message = "Le prix doit être positif")
    private BigDecimal prixUnitaire;

    private BigDecimal valeurTotale;

    @NotBlank(message = "Les conditions de livraison sont obligatoires")
    @Size(max = 2000, message = "Les conditions de livraison ne doivent pas dépasser 2000 caractères")
    private String conditionsLivraison;

    private String statut;
    private LocalDateTime createdDate;

    private String qrCode;
    private String qrImageBase64;
}