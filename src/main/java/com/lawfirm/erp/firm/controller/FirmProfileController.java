package com.lawfirm.erp.firm.controller;

import com.lawfirm.erp.common.constant.FirmConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.firm.request.SetFirmThemeRequest;
import com.lawfirm.erp.dto.firm.request.SetLogoAllowedRequest;
import com.lawfirm.erp.dto.firm.request.UpdateFirmProfileRequest;
import com.lawfirm.erp.dto.firm.response.FirmProfileResponse;
import com.lawfirm.erp.dto.firm.response.LogoResponse;
import com.lawfirm.erp.firm.service.FirmBrandService;
import com.lawfirm.erp.firm.service.FirmProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/firm")
@RequiredArgsConstructor
@Tag(name = "Firm Management", description = "Firm profile, brand and logo management APIs")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class FirmProfileController {

    private final FirmBrandService firmBrandService;
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

    @GetMapping("/brand/theme")
    @Operation(summary = FirmConstants.GET_FIRM_THEME_SUMMARY)
    public ResponseEntity<ApiResponse<FirmProfileResponse>> getFirmTheme() {
        return responseHandler.ok(
                firmBrandService.getMyFirmProfile(),
                "Firm theme fetched successfully"
        );
    }

    @PutMapping("/brand/theme")
    @Operation(summary = FirmConstants.UPDATE_FIRM_THEME_SUMMARY)
    public ResponseEntity<ApiResponse<FirmProfileResponse>> setFirmTheme(
            @Valid @RequestBody ApiRequest<SetFirmThemeRequest> request) {
        return responseHandler.ok(
                firmBrandService.setMyFirmTheme(request.getData()),
                "Firm theme updated successfully"
        );
    }

    @GetMapping("/brand/logo")
    @Operation(summary = FirmConstants.GET_FIRM_LOGO_SUMMARY)
    public ResponseEntity<ApiResponse<LogoResponse>> getFirmLogo() {
        return responseHandler.ok(
                firmBrandService.getMyFirmLogo(),
                "Firm logo settings fetched successfully"
        );
    }

    @PostMapping(value = "/brand/logo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = FirmConstants.UPLOAD_FIRM_LOGO_SUMMARY)
    public ResponseEntity<ApiResponse<LogoResponse>> uploadFirmLogo(
            @RequestPart("file") MultipartFile file) {
        return responseHandler.ok(
                firmBrandService.uploadMyFirmLogo(file),
                "Firm logo uploaded successfully"
        );
    }

    @PatchMapping("/brand/logo/allowed")
    @Operation(summary = FirmConstants.SET_FIRM_LOGO_ALLOWED_SUMMARY)
    public ResponseEntity<ApiResponse<LogoResponse>> setLogoAllowed(
            @Valid @RequestBody ApiRequest<SetLogoAllowedRequest> request) {
        return responseHandler.ok(
                firmBrandService.toggleLogoAllowed(request.getData().getLogoAllowed()),
                "Firm logo allowed setting updated successfully"
        );
    }
}