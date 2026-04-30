package com.lawfirm.erp.repository;

import com.lawfirm.erp.entity.User;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByMobileNo(String mobileNo);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByMobileNo(String mobileNo);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.role.roleName = :roleName")
    boolean existsByRoleName(@Param("roleName") String roleName);

    @Query("SELECT u FROM User u WHERE u.tenantType.code = :subdomain AND u.role.roleName = 'FIRM_ADMIN'")
    Optional<User> findBySubdomain(@Param("subdomain") String subdomain);
}
