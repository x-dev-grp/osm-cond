package com.osm.conditioning.projet.service;

import com.osm.conditioning.projet.dto.ClientDto;
import com.osm.conditioning.projet.entity.Client;
import com.osm.conditioning.projet.repository.ClientRepository;
import com.xdev.xdevbase.repos.BaseRepository;
import com.xdev.xdevbase.services.impl.BaseServiceImpl;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClientService extends BaseServiceImpl<Client, ClientDto, ClientDto> {

    private final ClientRepository clientRepository;
    private final ModelMapper modelMapper;

    @Autowired
    public ClientService(BaseRepository<Client> repository, ClientRepository clientRepository, ModelMapper modelMapper) {
        super(repository, modelMapper);
        this.clientRepository = clientRepository;
        this.modelMapper = modelMapper;
    }

    public List<ClientDto> getAllClients() {
        return clientRepository.findAllByIsDeletedFalse().stream()
                .map(client -> modelMapper.map(client, ClientDto.class))
                .collect(Collectors.toList());
    }

    public Client findClientEntityById(UUID id) {
        return clientRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("Client non trouvé avec id: " + id));
    }

    public ClientDto getClientById(UUID id) {
        Client client = findClientEntityById(id);
        return modelMapper.map(client, ClientDto.class);
    }

    @Transactional
    public ClientDto createClient(ClientDto clientDto) {
        if (!StringUtils.hasText(clientDto.getNom())) {
            throw new RuntimeException("Le nom du client est obligatoire");
        }
        clientDto.setCodeClient(genererCodeClient());
        if (clientDto.getEmail() != null && !clientDto.getEmail().isEmpty()) {
            if (clientRepository.existsByEmailAndIsDeletedFalse(clientDto.getEmail())) {
                throw new RuntimeException("Un client avec cet email existe déjà: " + clientDto.getEmail());
            }
        }
        if (clientDto.getNumeroTva() != null && !clientDto.getNumeroTva().isEmpty()) {
            if (clientRepository.existsByNumeroTvaAndIsDeletedFalse(clientDto.getNumeroTva())) {
                throw new RuntimeException("Un client avec ce numéro de TVA existe déjà: " + clientDto.getNumeroTva());
            }
        }
        if (clientDto.getSiret() != null && !clientDto.getSiret().isEmpty()) {
            if (clientRepository.existsBySiretAndIsDeletedFalse(clientDto.getSiret())) {
                throw new RuntimeException("Un client avec ce SIRET existe déjà: " + clientDto.getSiret());
            }
        }
        if (clientDto.getTelephone() != null && !clientDto.getTelephone().isEmpty()) {
            if (clientRepository.existsByTelephoneAndIsDeletedFalse(clientDto.getTelephone())) {
                throw new RuntimeException("Un client avec ce téléphone existe déjà: " + clientDto.getTelephone());
            }
        }
        Client client = modelMapper.map(clientDto, Client.class);
        client.setDeleted(false);
        client.setActif(true);

        Client savedClient = clientRepository.save(client);
        return modelMapper.map(savedClient, ClientDto.class);
    }

    @Transactional
    public ClientDto updateClient(UUID id, ClientDto clientDto) {
        Client existingClient = findClientEntityById(id);

        if (!StringUtils.hasText(clientDto.getNom())) {
            throw new RuntimeException("Le nom du client est obligatoire");
        }
        if (clientDto.getEmail() != null) {
            if (!clientDto.getEmail().equals(existingClient.getEmail())) {
                if (clientRepository.existsByEmailAndIsDeletedFalseAndIdNot(clientDto.getEmail(), id)) {
                    throw new RuntimeException("Un client avec cet email existe déjà: " + clientDto.getEmail());
                }
                existingClient.setEmail(clientDto.getEmail());
            }
        } else {
            existingClient.setEmail(null);
        }
        if (clientDto.getNumeroTva() != null) {
            if (!clientDto.getNumeroTva().equals(existingClient.getNumeroTva())) {
                if (clientRepository.existsByNumeroTvaAndIsDeletedFalseAndIdNot(clientDto.getNumeroTva(), id)) {
                    throw new RuntimeException("Un client avec ce numéro de TVA existe déjà: " + clientDto.getNumeroTva());
                }
                existingClient.setNumeroTva(clientDto.getNumeroTva());
            }
        } else {
            existingClient.setNumeroTva(null);
        }
        if (clientDto.getSiret() != null) {
            if (!clientDto.getSiret().equals(existingClient.getSiret())) {
                if (clientRepository.existsBySiretAndIsDeletedFalseAndIdNot(clientDto.getSiret(), id)) {
                    throw new RuntimeException("Un client avec ce SIRET existe déjà: " + clientDto.getSiret());
                }
                existingClient.setSiret(clientDto.getSiret());
            }
        } else {
            existingClient.setSiret(null);
        }
        if (clientDto.getTelephone() != null) {
            if (!clientDto.getTelephone().equals(existingClient.getTelephone())) {
                if (clientRepository.existsByTelephoneAndIsDeletedFalseAndIdNot(clientDto.getTelephone(), id)) {
                    throw new RuntimeException("Un client avec ce téléphone existe déjà: " + clientDto.getTelephone());
                }
                existingClient.setTelephone(clientDto.getTelephone());
            }
        } else {
            existingClient.setTelephone(null);
        }
        existingClient.setNom(clientDto.getNom());
        existingClient.setAdresse(clientDto.getAdresse());
        existingClient.setVille(clientDto.getVille());
        existingClient.setPays(clientDto.getPays());
        existingClient.setCodePostal(clientDto.getCodePostal());
        existingClient.setPrivateLabel(clientDto.getPrivateLabel());
        existingClient.setNotes(clientDto.getNotes());

        Client updatedClient = clientRepository.save(existingClient);
        return modelMapper.map(updatedClient, ClientDto.class);
    }

    @Transactional
    public void desactiverClient(UUID id) {
        Client client = findClientEntityById(id);
        client.setActif(false);
        clientRepository.save(client);
    }

    @Transactional
    public void activerClient(UUID id) {
        Client client = findClientEntityById(id);
        client.setActif(true);
        clientRepository.save(client);
    }

    @Transactional
    public void deleteClient(UUID id) {
        Client client = findClientEntityById(id);
        client.setDeleted(true);
        client.setActif(false);
        clientRepository.save(client);
    }

    private String genererCodeClient() {
        return generateBusinessCode("codeClient", "CL");
    }
}
