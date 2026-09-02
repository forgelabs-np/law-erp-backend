package com.lawfirm.erp.modules.usermanagement.service;

import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.modules.usermanagement.dto.request.BulkDeactivateRequest;
import com.lawfirm.erp.modules.usermanagement.dto.request.BulkRoleChangeRequest;
import com.lawfirm.erp.modules.usermanagement.dto.request.ResetPasswordRequest;
import com.lawfirm.erp.modules.usermanagement.dto.response.BulkOperationResult;
import com.lawfirm.erp.modules.usermanagement.dto.response.UserPermissionsResponse;
import com.lawfirm.erp.modules.usermanagement.dto.response.UserProfileResponse;
import com.lawfirm.erp.modules.usermanagement.dto.response.UserSummaryResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface UserManagementService {

    PagedResponse<UserSummaryResponse> listUsers(UserType userType, UUID roleId, Boolean isActive, int page, int size);

    PagedResponse<UserSummaryResponse> searchUsers(String query, int page, int size);

    UserProfileResponse getUserProfile(UUID userId);

    UserPermissionsResponse getUserPermissions(UUID userId);

    List<UserProfileResponse.ActivityEntry> getUserActivity(UUID userId, LocalDateTime from, LocalDateTime to, int page, int size);

    void resetPassword(UUID userId, ResetPasswordRequest request);

    BulkOperationResult bulkDeactivate(BulkDeactivateRequest request);

    BulkOperationResult bulkRoleChange(BulkRoleChangeRequest request);

    void deleteUser(UUID userId);
}
