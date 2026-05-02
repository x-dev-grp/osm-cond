package com.osm.conditioning.projet.dto;


import com.osm.conditioning.projet.entity.ProjetClient;
import com.osm.conditioning.projet.enums.TypeClient;
import com.xdev.xdevbase.dtos.BaseDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class ProjetClientDto extends BaseDto<ProjetClient> {
    private UUID id;
    @NotBlank
    private String nom;
    @Email
    @NotBlank
    private String email;
    private String telephone;
    private TypeClient type; // BUYER / BRAND_OWNER
    private String adresse;
}