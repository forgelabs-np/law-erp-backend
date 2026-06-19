package com.lawfirm.erp.rbac.repository;

import com.lawfirm.erp.rbac.entity.ModulePermission;
import com.lawfirm.erp.rbac.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModulePermissionRepository extends JpaRepository<ModulePermission, UUID> {

    @Modifying
    @Query("DELETE FROM ModulePermission mp WHERE mp.module.id = :moduleId")
    void deleteByModuleId(@Param("moduleId") UUID moduleId);

    @Query("SELECT mp.permission FROM ModulePermission mp WHERE mp.module.id = :moduleId")
    List<Permission> findPermissionsByModuleId(@Param("moduleId") UUID moduleId);

    @Query("SELECT COUNT(mp) FROM ModulePermission mp WHERE mp.module.id = :moduleId")
    long countByModuleId(@Param("moduleId") UUID moduleId);

}