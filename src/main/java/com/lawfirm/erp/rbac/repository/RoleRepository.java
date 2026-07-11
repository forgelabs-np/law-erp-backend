package com.lawfirm.erp.rbac.repository;

import com.lawfirm.erp.rbac.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByRoleCode(String roleCode);

    Optional<Role> findByRoleName(String roleName);

    boolean existsByRoleName(String roleName);

    boolean existsByRoleCode(String roleCode);

    @Query("SELECT COUNT(ur) FROM UserRole ur WHERE ur.role.id = :roleId")
    int countUsersByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT r FROM Role r WHERE r.firm IS NULL AND r.isSystem = true")
    List<Role> findByFirmIsNullAndIsSystemTrue();

    @Query("SELECT r FROM Role r WHERE r.firm.id = :firmId AND r.roleCode = :roleCode")
    Optional<Role> findByFirmIdAndRoleCode(@Param("firmId") UUID firmId, @Param("roleCode") String roleCode);

    @Query("SELECT r FROM Role r WHERE r.firm.id = :firmId AND r.isSystem = false")
    List<Role> findByFirmIdAndIsSystemFalse(@Param("firmId") UUID firmId);

    @Query("SELECT r FROM Role r WHERE r.roleCode = :roleCode AND r.firm IS NULL")
    Optional<Role> findByRoleCodeAndFirmIsNull(@Param("roleCode") String roleCode);
}
