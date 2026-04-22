package com.osm.production.dto;




import com.osm.production.Enum.QualityStatus;
import com.osm.production.Enum.StatutOF;
import com.osm.production.model.OrdreFabrication;
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
    private UUID skuId;
    private String skuCode;
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
}
