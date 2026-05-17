package com.osm.conditioning.service;

import com.osm.conditioning.dto.CertificationDto;
import com.osm.conditioning.model.Certification;
import com.osm.conditioning.repository.CertificationRepository;
import com.xdev.xdevbase.models.Action;
import com.xdev.xdevbase.services.impl.BaseServiceImpl;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CertificationService extends BaseServiceImpl<Certification, CertificationDto, CertificationDto> {

    private final CertificationRepository certificationRepository;

    public CertificationService(CertificationRepository certificationRepository, ModelMapper modelMapper) {
        super(certificationRepository, modelMapper);
        this.certificationRepository = certificationRepository;
    }

    @Override
    public Set<Action> actionsMapping(Certification certification) {
        return Set.of(Action.READ, Action.CREATE, Action.UPDATE, Action.DELETE, Action.VALIDATE, Action.GEN_PDF);
    }

    @Override
    @Transactional(readOnly = true)
    public CertificationDto findById(UUID id) {
        return super.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificationDto> findAll() {
        return super.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CertificationDto> findAll(int page, int size, String sort, String direction) {
        return super.findAll(page, size, sort, direction);
    }

    @Override
    public CertificationDto save(CertificationDto request) {
        validateCreateRequest(request);
        normalizeRequest(request);
        return super.save(request);
    }

    @Override
    public List<CertificationDto> save(List<CertificationDto> request) {
        if (request == null || request.isEmpty()) {
            return List.of();
        }

        request.forEach(item -> {
            validateCreateRequest(item);
            normalizeRequest(item);
        });

        return super.save(request);
    }

    @Override
    public CertificationDto update(CertificationDto request) {
        validateUpdateRequest(request);
        normalizeRequest(request);
        return super.update(request);
    }

    @Override
    public void remove(UUID id) {
        super.remove(id);
    }

    @Override
    public CertificationDto delete(UUID id) {
        return super.delete(id);
    }

    @Transactional(readOnly = true)
    public CertificationDto findByName(String name) {
        Certification certification = certificationRepository.findByNameAndIsDeletedFalse(name).orElseThrow(() -> new EntityNotFoundException("Certification not found with name: " + name));

        return modelMapper.map(certification, CertificationDto.class);
    }

    @Transactional(readOnly = true)
    public CertificationDto findByCode(String code) {
        String normalizedCode = normalizeCode(code);

        Certification certification = certificationRepository.findByCodeAndIsDeletedFalse(normalizedCode).orElseThrow(() -> new EntityNotFoundException("Certification not found with code: " + normalizedCode));

        return modelMapper.map(certification, CertificationDto.class);
    }

    @Override
    public void resolveEntityRelations(Certification entity) {
        // No relation to resolve for now.
        // Keep this override for future relations: logo file, certificate authority, label rules, etc.
    }

    private void validateCreateRequest(CertificationDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Certification request is required");
        }

        if (isBlank(request.getName())) {
            throw new IllegalArgumentException("Certification name is required");
        }

        if (certificationRepository.existsByNameAndIsDeletedFalse(request.getName().trim())) {
            throw new EntityExistsException("Certification already exists with name: " + request.getName());
        }

        String code = normalizeCode(request.getCode());
        if (!isBlank(code) && certificationRepository.existsByCodeAndIsDeletedFalse(code)) {
            throw new EntityExistsException("Certification already exists with code: " + code);
        }
    }

    private void validateUpdateRequest(CertificationDto request) {
        if (request == null || request.getId() == null) {
            throw new IllegalArgumentException("Certification id is required for update");
        }

        if (isBlank(request.getName())) {
            throw new IllegalArgumentException("Certification name is required");
        }

        if (certificationRepository.existsByNameAndIdNotAndIsDeletedFalse(request.getName().trim(), request.getId())) {
            throw new EntityExistsException("Another certification already exists with name: " + request.getName());
        }

        String code = normalizeCode(request.getCode());
        if (!isBlank(code) && certificationRepository.existsByCodeAndIdNotAndIsDeletedFalse(code, request.getId())) {
            throw new EntityExistsException("Another certification already exists with code: " + code);
        }
    }

    private void normalizeRequest(CertificationDto request) {
        if (request == null) {
            return;
        }

        if (request.getName() != null) {
            request.setName(request.getName().trim());
        }

        request.setCode(normalizeCode(request.getCode()));

        if (request.getIssuingBody() != null) {
            request.setIssuingBody(request.getIssuingBody().trim());
        }

        if (request.getWebsiteUrl() != null) {
            request.setWebsiteUrl(request.getWebsiteUrl().trim());
        }

        if (request.getCategory() != null) {
            request.setCategory(request.getCategory().trim().toUpperCase());
        }

        if (request.getIsActive() == null) {
            request.setIsActive(true);
        }
    }

    private String normalizeCode(String code) {
        if (isBlank(code)) {
            return null;
        }

        return code.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
