package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.dto.admin.request.PermissionRequest;
import com.lawfirm.erp.dto.admin.response.GroupedPermissionResponse;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;

import java.util.List;
import java.util.UUID;

public interface PermissionService {

    PermissionResponse upsert(PermissionRequest request);

    void delete(UUID id);

    PermissionResponse toggleStatus(UUID id);

    List<PermissionResponse> findAll();

    List<PermissionResponse> findActive();

    PermissionResponse findById(UUID id);

    GroupedPermissionResponse findAllGroupedByModule();
}
