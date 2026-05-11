package com.osm.conditioning.projet.controller;

import com.osm.conditioning.projet.dto.ClientDto;
import com.osm.conditioning.projet.entity.Client;
import com.osm.conditioning.projet.service.ClientService;
import com.xdev.xdevbase.apiDTOs.ApiResponse;
import com.xdev.xdevbase.controllers.impl.BaseControllerImpl;
import com.xdev.xdevbase.services.BaseService;
import com.xdev.xdevbase.utils.OSMLogger;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.modelmapper.ModelMapper;
import org.modelmapper.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ordreConditionement/clients")

public class ClientController extends BaseControllerImpl<Client, ClientDto, ClientDto> {

    private static final Logger log = LoggerFactory.getLogger(ClientController.class);
    private final ClientService clientService;

    @Autowired
    public ClientController(BaseService<Client, ClientDto, ClientDto> baseService,
                            ModelMapper modelMapper,
                            ClientService clientService) {
        super(baseService, modelMapper);
        this.clientService = clientService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Client, ClientDto>> getAllClients() {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "getAllClients");
        try {
            List<ClientDto> clients = clientService.getAllClients();
            log.debug("Successfully fetched {} clients", clients.size());
            return ResponseEntity.ok(new ApiResponse<>(true, "Clients retrieved successfully", clients));
        } catch (Exception e) {
            log.error("Error fetching all clients: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving clients: " + e.getMessage(), null));
        } finally {
            OSMLogger.logMethodExit(this.getClass(), "getAllClients");
            OSMLogger.logPerformance(this.getClass(), "getAllClients", startTime, System.currentTimeMillis());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Client, ClientDto>> getClientById(@PathVariable UUID id) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "getClientById", id);
        try {
            ClientDto client = clientService.getClientById(id);
            log.debug("Successfully fetched client with id: {}", id);
            return ResponseEntity.ok(new ApiResponse<>(true, "Client retrieved successfully", Arrays.asList(client)));
        } catch (NotFoundException e) {
            log.warn("Client not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error fetching client with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error retrieving client: " + e.getMessage(), null));
        } finally {
            OSMLogger.logMethodExit(this.getClass(), "getClientById");
            OSMLogger.logPerformance(this.getClass(), "getClientById", startTime, System.currentTimeMillis());
        }
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Client, ClientDto>> createClient(@Valid @RequestBody ClientDto clientDto) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "createClient");
        try {
             ClientDto created = clientService.createClient(clientDto);
            log.info("Successfully created client with id: {}", created.getId());
            return new ResponseEntity<>(new ApiResponse<>(true, "Client created successfully", Arrays.asList(created)),
                    HttpStatus.CREATED);
        } catch (ValidationException | BadRequestException e) {
            log.warn("Validation error creating client: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            log.error("Bad request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Error creating client: " + e.getMessage(), null));
        } catch (HttpMessageNotWritableException e) {
            log.error("Serialization error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Error serializing response: " + e.getMessage(), null));
        } catch (Exception e) {
            log.error("Unexpected error creating client: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Unexpected error creating client: " + e.getMessage(), null));
        } finally {
            OSMLogger.logMethodExit(this.getClass(), "createClient");
            OSMLogger.logPerformance(this.getClass(), "createClient", startTime, System.currentTimeMillis());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Client, ClientDto>> updateClient(
            @PathVariable UUID id,
            @Valid @RequestBody ClientDto clientDto) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "updateClient", id);
        try {
             ClientDto updated = clientService.updateClient(id, clientDto);
            log.info("Successfully updated client with id: {}", id);
            return ResponseEntity.ok(new ApiResponse<>(true, "Client updated successfully", Arrays.asList(updated)));
        } catch (NotFoundException e) {
            log.warn("Client not found for update with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (ValidationException | BadRequestException e) {
            log.warn("Validation error updating client with id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            log.error("Bad request for client update {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Error updating client: " + e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error updating client with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error updating client: " + e.getMessage(), null));
        } finally {
            OSMLogger.logMethodExit(this.getClass(), "updateClient");
            OSMLogger.logPerformance(this.getClass(), "updateClient", startTime, System.currentTimeMillis());
        }
    }

    @PutMapping("/{id}/desactiver")
    public ResponseEntity<ApiResponse<Client, ClientDto>> desactiverClient(@PathVariable UUID id) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "desactiverClient", id);
        try {
            clientService.desactiverClient(id);
            log.info("Successfully deactivated client with id: {}", id);
            return ResponseEntity.ok(new ApiResponse<>(true, "Client deactivated successfully", null));
        } catch (NotFoundException e) {
            log.warn("Client not found for deactivation with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (BadRequestException e) {
            log.warn("Bad request for client deactivation {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error deactivating client with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error deactivating client: " + e.getMessage(), null));
        } finally {
            OSMLogger.logMethodExit(this.getClass(), "desactiverClient");
            OSMLogger.logPerformance(this.getClass(), "desactiverClient", startTime, System.currentTimeMillis());
        }
    }

    @PutMapping("/{id}/activer")
    public ResponseEntity<ApiResponse<Client, ClientDto>> activerClient(@PathVariable UUID id) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "activerClient", id);
        try {
            clientService.activerClient(id);
            log.info("Successfully activated client with id: {}", id);
            return ResponseEntity.ok(new ApiResponse<>(true, "Client activated successfully", null));
        } catch (NotFoundException e) {
            log.warn("Client not found for activation with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (BadRequestException e) {
            log.warn("Bad request for client activation {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error activating client with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error activating client: " + e.getMessage(), null));
        } finally {
            OSMLogger.logMethodExit(this.getClass(), "activerClient");
            OSMLogger.logPerformance(this.getClass(), "activerClient", startTime, System.currentTimeMillis());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Client, ClientDto>> deleteClient(@PathVariable UUID id) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "deleteClient", id);
        try {
            clientService.deleteClient(id);
            log.info("Successfully deleted client with id: {}", id);
            return ResponseEntity.noContent().build();
        } catch (NotFoundException e) {
            log.warn("Client not found for deletion with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error deleting client with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Error deleting client: " + e.getMessage(), null));
        } finally {
            OSMLogger.logMethodExit(this.getClass(), "deleteClient");
            OSMLogger.logPerformance(this.getClass(), "deleteClient", startTime, System.currentTimeMillis());
        }
    }



    @Override
    protected String getResourceName() {
        return "Client".toUpperCase();
    }

    @Override
    public ResponseEntity<?> resolve(String publicCode) {
        return null;
    }
}