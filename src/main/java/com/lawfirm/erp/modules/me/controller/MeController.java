package com.lawfirm.erp.modules.me.controller;

import com.lawfirm.erp.common.constant.MeConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.me.dto.MeResponse;
import com.lawfirm.erp.modules.me.service.MeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Me", description = "Current user identity — used by frontend on app load")
public class MeController {

    private final MeService meService;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = MeConstants.GET_ME_SUMMARY, description = MeConstants.GET_ME_DESCRIPTION)
    public ResponseEntity<ApiResponse<MeResponse>> getMe() {
        return responseHandler.ok(meService.getMe(), "User identity fetched");
    }
}
