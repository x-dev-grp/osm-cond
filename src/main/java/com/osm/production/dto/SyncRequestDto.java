package com.osm.production.dto;

import com.osm.production.model.OfflineOperation;
import com.xdev.xdevbase.dtos.BaseDto;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class SyncRequestDto {
    private String operationId;
    private String url;
    private String method;
    private String body;
}