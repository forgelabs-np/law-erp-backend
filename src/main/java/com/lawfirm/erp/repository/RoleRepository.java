package com.lawfirm.erp.repository;

import com.lawfirm.erp.entity.Role;
import com.lawfirm.erp.enums.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleType name);
    boolean existsByName(RoleType name);

}
