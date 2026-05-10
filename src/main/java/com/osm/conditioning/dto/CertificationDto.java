package com.osm.conditioning.dto;

import com.osm.conditioning.model.Certification;
import com.xdev.xdevbase.dtos.BaseDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CertificationDto extends BaseDto<Certification> {
    private String name;
    private String code;
    private String description;
    private String issuingBody;
    private String logoData;
    private String logoContentType;
    private String websiteUrl;
    private String category;
    private Boolean isActive;
}
