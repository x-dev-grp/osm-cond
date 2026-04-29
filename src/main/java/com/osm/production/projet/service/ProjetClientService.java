package com.osm.production.projet.service;

import com.osm.production.projet.dto.ProjetClientDto;
import com.osm.production.projet.entity.ProjetClient;
import com.osm.production.projet.repository.ProjetClientRepository;
import com.xdev.xdevbase.config.TenantContext;
import com.xdev.xdevbase.qr.CodeGenerator;
import com.xdev.xdevbase.repos.BaseRepository;
import com.xdev.xdevbase.services.impl.BaseServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjetClientService extends BaseServiceImpl<ProjetClient, ProjetClientDto, ProjetClientDto> {

    private final ProjetClientRepository projetClientRepository;

    public ProjetClientService(
            BaseRepository<ProjetClient> repository,
            CodeGenerator codeGenerator,
            ModelMapper modelMapper,
            ProjetClientRepository projetClientRepository) {
        super(repository, modelMapper);
        this.projetClientRepository = projetClientRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjetClientDto> findAll() {
        return projetClientRepository.findAllByIsDeletedFalse()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjetClientDto findById(UUID id) {
        ProjetClient client = projetClientRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("ProjetClient non trouve : " + id));
        return toDto(client);
    }

    @Transactional
    public ProjetClientDto create(ProjetClientDto dto) {
        ProjetClient client = new ProjetClient();
        client.setNom(dto.getNom());
        client.setEmail(dto.getEmail());
        client.setTelephone(dto.getTelephone());
        client.setType(dto.getType());
        client.setAdresse(dto.getAdresse());
        client.setTenantId(TenantContext.getCurrentTenant());

        ProjetClient saved = projetClientRepository.save(client);
        return toDto(saved);
    }

    @Override
    @Transactional
    public ProjetClientDto save(ProjetClientDto dto) {
        return create(dto);
    }

    @Transactional
    public ProjetClientDto update(UUID id, ProjetClientDto dto) {
        ProjetClient client = projetClientRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("ProjetClient non trouve : " + id));
        client.setNom(dto.getNom());
        client.setEmail(dto.getEmail());
        client.setTelephone(dto.getTelephone());
        client.setType(dto.getType());
        client.setAdresse(dto.getAdresse());
        return toDto(projetClientRepository.save(client));
    }

    @Override
    @Transactional
    public ProjetClientDto update(ProjetClientDto dto) {
        if (dto == null || dto.getId() == null) {
            throw new IllegalArgumentException("L'id du ProjetClient est obligatoire pour la mise a jour");
        }
        return update(dto.getId(), dto);
    }

    @Override
    @Transactional
    public ProjetClientDto delete(UUID id) {
        ProjetClient client = projetClientRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("ProjetClient non trouve : " + id));
        client.setDeleted(true);
        ProjetClient saved = projetClientRepository.save(client);
        return toDto(saved);
    }

    public ProjetClient findByIdOrThrow(UUID id) {
        return projetClientRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("ProjetClient non trouve : " + id));
    }

    private ProjetClientDto toDto(ProjetClient client) {
        ProjetClientDto dto = new ProjetClientDto();
        dto.setId(client.getId());
        dto.setNom(client.getNom());
        dto.setEmail(client.getEmail());
        dto.setTelephone(client.getTelephone());
        dto.setType(client.getType());
        dto.setAdresse(client.getAdresse());
        return dto;
    }
}
