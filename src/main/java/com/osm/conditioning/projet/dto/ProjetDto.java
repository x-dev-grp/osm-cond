package com.osm.conditioning.projet.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.osm.conditioning.model.OrdreFabrication;
import com.osm.conditioning.projet.entity.Projet;
import com.osm.conditioning.projet.enums.TypeEmballage;
import com.osm.conditioning.projet.enums.TypeProduit;
import com.xdev.xdevbase.dtos.BaseDto;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjetDto extends BaseDto<Projet> {

    private String code;

    private ClientDto client;

    @JsonAlias({"client_id", "projetClientId", "client_id"})
    private UUID clientId;

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

    @JsonAlias("skuId")
    private UUID productId;
    private String productName;
    private UUID bomId;

    @JsonProperty("skuId")
    public UUID getSkuId() {
        return productId;
    }

    public void setSkuId(UUID skuId) {
        this.productId = skuId;
    }

    @JsonProperty("skuCode")
    public String getSkuCode() {
        return productName;
    }

    public void setSkuCode(String skuCode) {
        this.productName = skuCode;
    }

    @JsonProperty("clientId")
    public UUID getClientId() {
        if (clientId != null) {
            return clientId;
        }

        return client != null ? client.getId() : null;
    }

    public void setClient(ClientDto client) {
        this.client = client;
        if (client != null && client.getId() != null) {
            this.clientId = client.getId();
        }
    }
}
