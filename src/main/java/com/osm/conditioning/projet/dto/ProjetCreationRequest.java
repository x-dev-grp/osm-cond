package com.osm.conditioning.projet.dto;


import com.osm.conditioning.projet.enums.TypeEmballage;
import com.osm.conditioning.projet.enums.TypeProduit;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ProjetCreationRequest {

    @NotNull
    private UUID clientId;

    @NotNull
    private TypeProduit typeProduit;

    @NotNull
    private TypeEmballage typeEmballage;

    @Positive
    private Double quantiteCible;

    @NotBlank
    private String unite;  // "LITRES" ou "UNITES"

    @FutureOrPresent
    private LocalDate dateLimiteLivraison;

    @Positive
    private BigDecimal prixUnitaire;

    @NotBlank
    @Size(max = 2000)
    private String conditionsLivraison;
}