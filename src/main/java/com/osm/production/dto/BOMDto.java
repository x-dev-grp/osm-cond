package com.osm.production.dto;

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
    private UUID skuId;
    private String version;
    private List<BomLineDto> lines;
}
