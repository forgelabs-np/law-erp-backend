package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.auth.security.ReadScopeGuard;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.modules.casemanagement.dto.response.ClientMatterResponse;
import com.lawfirm.erp.modules.casemanagement.service.ClientMatterPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/client/matters")
@RequiredArgsConstructor
@Tag(name = "Client Portal - Matters", description = "A client's own cases")
public class ClientMatterPortalController {

    private final ClientMatterPortalService clientMatterPortalService;
    private final ReadScopeGuard readScopeGuard;

    @GetMapping
    @Operation(summary = "List my cases",
            description = "Every matter linked to the signed-in client account")
    public ApiResponse<List<ClientMatterResponse>> listMyMatters() {
        requireClientScope();
        return ApiResponse.success(clientMatterPortalService.listMyMatters());
    }

    @GetMapping("/{matterNumber}")
    @Operation(summary = "Get one of my cases")
    public ApiResponse<ClientMatterResponse> getMyMatter(@PathVariable String matterNumber) {
        requireClientScope();
        return ApiResponse.success(clientMatterPortalService.getMyMatter(matterNumber));
    }

    // Controller-level guard: the client portal is for CLIENT accounts only. Firm staff must use
    // the internal surfaces, which enforce their own scoping.
    private void requireClientScope() {
        if (!readScopeGuard.isClientScope()) {
            throw new ForbiddenException("This endpoint is for client portal accounts only");
        }
    }
}
