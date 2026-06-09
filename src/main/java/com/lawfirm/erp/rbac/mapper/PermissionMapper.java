package com.lawfirm.erp.rbac.mapper;

import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PermissionMapper {

    // Parameterless methods
    List<PermissionResponse> findAllPermissions();
    List<PermissionResponse> findAllActivePermissions();

    // Paginated versions
    List<PermissionResponse> findAllPermissionsWithPagination(@Param("limit") Integer limit, @Param("offset") Integer offset);

    // Single record
    PermissionResponse findPermissionById(@Param("permissionId") UUID permissionId);

    // By module
    List<PermissionResponse> findPermissionsByModuleId(@Param("moduleId") UUID moduleId,
                                                       @Param("activeOnly") Boolean activeOnly);
    List<PermissionResponse> findPermissionsByModuleCodes(@Param("moduleCodes") List<String> moduleCodes,
                                                          @Param("activeOnly") Boolean activeOnly);

    // By role
    List<PermissionResponse> findPermissionsByRoleId(@Param("roleId") UUID roleId);

    // Bulk permission check
    List<String> checkRolePermissions(@Param("roleId") UUID roleId,
                                      @Param("permissionCodes") List<String> permissionCodes);

    // Search and count
    long countPermissions(@Param("moduleId") UUID moduleId,
                          @Param("activeOnly") Boolean activeOnly,
                          @Param("search") String search);
    List<PermissionResponse> searchPermissions(@Param("moduleId") UUID moduleId,
                                               @Param("action") String action,
                                               @Param("activeOnly") Boolean activeOnly,
                                               @Param("search") String search,
                                               @Param("limit") Integer limit,
                                               @Param("offset") Integer offset);
}