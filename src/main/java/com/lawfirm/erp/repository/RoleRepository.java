// repository/RoleRepository.java
package com.lawfirm.erp.repository;

import com.lawfirm.erp.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(String roleName);

    Optional<Role> findByRoleCode(String roleCode);

    boolean existsByRoleName(String roleName);

    boolean existsByRoleCode(String roleCode);
}