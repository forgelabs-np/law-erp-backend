package com.lawfirm.erp.firm.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.firm.request.CreateClientRequest;
import com.lawfirm.erp.dto.firm.response.ClientResponse;
import com.lawfirm.erp.firm.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/firm/clients")
@RequiredArgsConstructor
@Tag(name = "Client Management", description = "Firm client management APIs")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class ClientController {

    private final ClientService clientService;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = "Create client")
    public ResponseEntity<ApiResponse<ClientResponse>> createClient(
            @Valid @RequestBody ApiRequest<CreateClientRequest> request) {
        return responseHandler.ok(
                clientService.createClient(request.getData()),
                "Client created successfully"
        );
    }

    @GetMapping
    @Operation(summary = "Get all clients (paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<ClientResponse>>> getAllClients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responseHandler.ok(
                clientService.getAllClients(page, size),
                "Clients fetched successfully"
        );
    }

    @GetMapping("/{clientId}")
    @Operation(summary = "Get client by ID")
    public ResponseEntity<ApiResponse<ClientResponse>> getClientById(@PathVariable UUID clientId) {
        return responseHandler.ok(
                clientService.getClientById(clientId),
                "Client fetched successfully"
        );
    }

    @PatchMapping("/{clientId}/portal-access")
    @Operation(summary = "Enable/disable client portal access")
    public ResponseEntity<ApiResponse<ClientResponse>> togglePortalAccess(
            @PathVariable UUID clientId,
            @Valid @RequestBody ApiRequest<Boolean> request) {
        return responseHandler.ok(
                clientService.togglePortalAccess(clientId, request.getData()),
                "Portal access updated successfully"
        );
    }
}