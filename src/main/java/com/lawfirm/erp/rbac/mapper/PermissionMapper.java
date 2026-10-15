package com.lawfirm.erp.rbac.mapper;

import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PermissionMapper {

    List<PermissionResponse> findAllPermissions();
    List<PermissionResponse> findAllActivePermissions();

    List<PermissionResponse> findAllPermissionsWithPagination(@Param("limit") Integer limit, @Param("offset") Integer offset);

    PermissionResponse findPermissionById(@Param("permissionId") UUID permissionId);

    List<PermissionResponse> findPermissionsByModuleId(@Param("moduleId") UUID moduleId,
                                                       @Param("activeOnly") Boolean activeOnly);
    List<PermissionResponse> findPermissionsByModuleCodes(@Param("moduleCodes") List<String> moduleCodes,
                                                          @Param("activeOnly") Boolean activeOnly);

    List<PermissionResponse> findPermissionsByRoleId(@Param("roleId") UUID roleId);

    List<String> checkRolePermissions(@Param("roleId") UUID roleId,
                                      @Param("permissionCodes") List<String> permissionCodes);

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