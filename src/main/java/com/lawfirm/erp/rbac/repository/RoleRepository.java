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

    @Query("SELECT r FROM Role r WHERE r.roleCode = :roleCode")
    Optional<Role> findByRoleCode(@Param("roleCode") String roleCode);

//    Optional<Role> findByRoleCode(String roleCode);

    Optional<Role> findByRoleName(String roleName);

    boolean existsByRoleName(String roleName);

    boolean existsByRoleCode(String roleCode);

    @Query("SELECT COUNT(ur) FROM UserRole ur WHERE ur.role.id = :roleId")
    int countUsersByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT r FROM Role r WHERE r.firm IS NULL AND r.isSystem = true")
    List<Role> findByFirmIsNullAndIsSystemTrue();
//
//    @Query("SELECT r FROM Role r WHERE r.firm.id = :firmId AND r.roleCode = :roleCode")
//    Optional<Role> findByFirmIdAndRoleCode(@Param("firmId") UUID firmId, @Param("roleCode") String roleCode);

    @Query("SELECT r FROM Role r WHERE r.firm.id = :firmId AND r.isSystem = false")
    List<Role> findByFirmIdAndIsSystemFalse(@Param("firmId") UUID firmId);

    /** Custom roles created before parent_role_id was set at creation — Phase 0 backfill target. */
    List<Role> findByParentRoleIdIsNullAndIsSystemFalse();

    /** All firm-scoped clones derived from a system template — template sync fan-out target. */
    List<Role> findByParentRoleId(UUID parentRoleId);

    @Query("SELECT r FROM Role r WHERE r.roleCode = :roleCode AND r.firm IS NULL")
    Optional<Role> findByRoleCodeAndFirmIsNull(@Param("roleCode") String roleCode);

    //  ADD THIS NEW METHOD - for cases where multiple roles exist
    @Query("SELECT r FROM Role r WHERE r.roleCode = :roleCode")
    List<Role> findAllByRoleCode(@Param("roleCode") String roleCode);


    // For DataInitializer — only system roles
    @Query("SELECT r FROM Role r WHERE r.roleCode = :roleCode AND r.firm IS NULL")
    Optional<Role> findSystemRoleByCode(@Param("roleCode") String roleCode);

    // For FirmService / FirmAdminService — only firm-scoped roles
    @Query("SELECT r FROM Role r WHERE r.firm.id = :firmId AND r.roleCode = :roleCode")
    Optional<Role> findByFirmIdAndRoleCode(@Param("firmId") UUID firmId,
                                           @Param("roleCode") String roleCode);

    // For super admin listing — all users with a role in a specific firm
    @Query("SELECT r FROM Role r WHERE r.roleCode = :roleCode AND r.firm.id = :firmId")
    Optional<Role> findFirmRoleByCode(@Param("firmId") UUID firmId,
                                      @Param("roleCode") String roleCode);

    /** Active roles only — avoids loading + filtering in Java. */
    @Query("SELECT r FROM Role r WHERE r.active = true ORDER BY r.roleName ASC")
    List<Role> findAllActive();

    /** Count permissions for a role in a single query — avoids N+1 in role listing. */
    @Query("SELECT COUNT(rp) FROM RolePermission rp WHERE rp.role.id = :roleId")
    long countPermissionsByRoleId(@Param("roleId") UUID roleId);
}
