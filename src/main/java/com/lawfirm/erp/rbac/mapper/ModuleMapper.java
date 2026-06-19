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

    List<ModuleResponse> findSubModulesByParentId(@Param("id") UUID parentId);

    List<ModuleResponse> findAllModulesFlat();

    int countPermissionsByModuleId(@Param("moduleId") UUID moduleId);

    List<ModuleResponse> findModulesByLevel(@Param("level") Integer level);

    List<ModuleResponse> searchModules(@Param("search") String search,
                                       @Param("limit") Integer limit,
                                       @Param("offset") Integer offset);
}