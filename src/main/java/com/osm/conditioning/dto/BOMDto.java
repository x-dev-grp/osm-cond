package com.osm.conditioning.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;
@Data
@Getter
@Setter
public class BOMDto {
    private UUID id;
    @JsonAlias("skuId")
    private UUID productId;
    private String productName;
    private String version;
    private List<BomLineDto> lines;

    @JsonProperty("skuId")
    public UUID getSkuId() {
        return productId;
    }

    public void setSkuId(UUID skuId) {
        this.productId = skuId;
    }
}
