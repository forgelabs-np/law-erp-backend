package com.lawfirm.erp.firm.controller;

import com.lawfirm.erp.common.constant.FirmConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.firm.request.UpdateFirmProfileRequest;
import com.lawfirm.erp.dto.firm.response.FirmProfileResponse;
import com.lawfirm.erp.firm.service.FirmProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/firm")
@RequiredArgsConstructor
@Tag(name = "Firm Management", description = "Firm profile management APIs")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class FirmProfileController {

    private final FirmProfileService firmProfileService;
    private final ResponseHandler responseHandler;

    @GetMapping("/profile")
    @Operation(summary = FirmConstants.GET_FIRM_PROFILE_SUMMARY)
    public ResponseEntity<ApiResponse<FirmProfileResponse>> getProfile() {
        return responseHandler.ok(
                firmProfileService.getMyFirmProfile(),
                "Firm profile fetched successfully"
        );
    }

    @PutMapping("/profile")
    @PostMapping("/profile")
    @Operation(summary = FirmConstants.UPDATE_FIRM_PROFILE_SUMMARY)
    public ResponseEntity<ApiResponse<FirmProfileResponse>> updateProfile(
            @Valid @RequestBody ApiRequest<UpdateFirmProfileRequest> request) {
        return responseHandler.ok(
                firmProfileService.updateMyFirmProfile(request.getData()),
                "Firm profile updated successfully"
        );
    }
}