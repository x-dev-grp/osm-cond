package com.osm.conditioning.projet.dto;



import com.osm.conditioning.projet.entity.Client;
import com.xdev.communicator.models.enums.ClientType;
import com.xdev.xdevbase.dtos.BaseDto;
import lombok.*;

import java.io.Serializable;


@Getter
@Setter
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClientDto extends BaseDto<Client> implements Serializable {
    private String nom;
    private String codeClient;
    private String email;
    private String telephone;
    private String adresse;
    private String ville;
    private String pays;
    private String codePostal;
    private Boolean privateLabel;
    private String siret;
    private String numeroTva;
    private String notes;
    private Boolean actif = true;
    private String qrImageBase64;
    private ClientType type;
}