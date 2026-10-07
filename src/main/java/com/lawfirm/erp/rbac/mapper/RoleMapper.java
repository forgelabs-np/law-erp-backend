package com.lawfirm.erp.rbac.mapper;

import com.lawfirm.erp.dto.admin.response.RoleResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface RoleMapper {

    List<RoleResponse> findAllRoles();
    List<RoleResponse> findAllActiveRoles();
    List<RoleResponse> findSystemRoles();
    List<RoleResponse> findCustomRoles();

    List<RoleResponse> findAllRolesWithPagination(@Param("limit") Integer limit, @Param("offset") Integer offset);

    RoleResponse findRoleById(@Param("roleId") UUID roleId);

    List<RoleResponse> findRolesByUserId(@Param("userId") UUID userId);
    List<RoleResponse> findRolesByFirmId(@Param("firmId") UUID firmId);

    List<RoleResponse> searchRoles(@Param("search") String search,
                                   @Param("activeOnly") Boolean activeOnly,
                                   @Param("systemOnly") Boolean systemOnly,
                                   @Param("excludeSystem") Boolean excludeSystem,
                                   @Param("limit") Integer limit,
                                   @Param("offset") Integer offset);
    long countRoles(@Param("activeOnly") Boolean activeOnly,
                    @Param("excludeSystem") Boolean excludeSystem);

    Map<String, Object> getRoleStatistics(@Param("roleId") UUID roleId);
}