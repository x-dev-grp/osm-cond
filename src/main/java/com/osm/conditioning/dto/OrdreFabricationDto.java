package com.osm.conditioning.dto;


import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.osm.conditioning.Enum.QualityStatus;
import com.osm.conditioning.Enum.StatutOF;
import com.osm.conditioning.model.OrdreFabrication;
import com.xdev.xdevbase.dtos.BaseDto;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Getter
@Setter
public class OrdreFabricationDto extends BaseDto<OrdreFabrication> {
    private UUID id;
    private String code;
    private StatutOF statut;
    private LocalDateTime dateDebutPrevue;
    private LocalDateTime dateFinPrevue;
    private LocalDateTime dateDebutReelle;
    private LocalDateTime dateFinReelle;
    private BigDecimal quantiteCible;
    private BigDecimal quantiteBonne;
    private BigDecimal quantiteNC;
    private Long dureeReelle;
    @JsonAlias("skuId")
    private UUID productId;
    private String productName;
    private UUID ligneId;
    private String ligneNom;
    private UUID lotVracId;
    private List<LigneOFDto> lignes;
    private UUID bomId;
     private String motifNC;
    private String publicCode;
    private String qrUrl;
    private String qrImageBase64;
    private QualityStatus qualityStatus;
    private UUID projectId;
    private String projectCode;

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
}
