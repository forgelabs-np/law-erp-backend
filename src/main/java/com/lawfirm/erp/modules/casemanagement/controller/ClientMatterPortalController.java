package com.lawfirm.erp.modules.casemanagement.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.modules.casemanagement.dto.response.ClientMatterResponse;
import com.lawfirm.erp.modules.casemanagement.service.ClientMatterPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Client portal — "my cases". Requires an enabled client account; the response is
 * always scoped to the calling client, so no id from the request can widen it.
 */
@RestController
@RequestMapping("/api/v1/client/matters")
@RequiredArgsConstructor
@Tag(name = "Client Portal - Matters", description = "A client's own cases")
public class ClientMatterPortalController {

    private final ClientMatterPortalService clientMatterPortalService;

    @GetMapping
    @Operation(summary = "List my cases",
            description = "Every matter linked to the signed-in client account")
    public ApiResponse<List<ClientMatterResponse>> listMyMatters() {
        return ApiResponse.success(clientMatterPortalService.listMyMatters());
    }

    @GetMapping("/{matterNumber}")
    @Operation(summary = "Get one of my cases")
    public ApiResponse<ClientMatterResponse> getMyMatter(@PathVariable String matterNumber) {
        return ApiResponse.success(clientMatterPortalService.getMyMatter(matterNumber));
    }
}
