package com.lawfirm.erp.rbac.mapper;

import com.lawfirm.erp.dto.admin.response.ModuleResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ModuleMapper {

    List<ModuleResponse> findAllModules();

    List<ModuleResponse> findAllActiveModules();

    ModuleResponse findModuleById(@Param("moduleId") UUID moduleId);

    int countPermissionsByModuleId(@Param("moduleId") UUID moduleId);
}