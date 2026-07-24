package com.lawfirm.erp.common.repository;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.common.enums.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsername(@Param("username") String username);

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.firm.id = :firmId")
    Optional<User> findByUsernameAndFirmId(@Param("username") String username, @Param("firmId") UUID firmId);

    @Query("SELECT u FROM User u WHERE u.mobileNo = :mobileNo AND u.firm.id = :firmId")
    Optional<User> findByMobileNoAndFirmId(@Param("mobileNo") String mobileNo, @Param("firmId") UUID firmId);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.firm.id = :firmId")
    Optional<User> findByEmailAndFirmId(@Param("email") String email, @Param("firmId") UUID firmId);

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.userType = :userType")
    Optional<User> findByUsernameAndUserType(@Param("username") String username, @Param("userType") UserType userType);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.userType = :userType")
    Optional<User> findByEmailAndUserType(@Param("email") String email, @Param("userType") UserType userType);

    @Query("SELECT u FROM User u WHERE u.firm.id = :firmId")
    List<User> findByFirmId(@Param("firmId") UUID firmId);

    @Query("SELECT u FROM User u WHERE u.firm.id = :firmId AND u.userType = :userType")
    List<User> findByFirmIdAndUserType(@Param("firmId") UUID firmId, @Param("userType") UserType userType);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.username = :username AND u.firm.id = :firmId")
    boolean existsByUsernameAndFirmId(@Param("username") String username, @Param("firmId") UUID firmId);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email AND u.firm.id = :firmId")
    boolean existsByEmailAndFirmId(@Param("email") String email, @Param("firmId") UUID firmId);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.mobileNo = :mobileNo AND u.firm.id = :firmId")
    boolean existsByMobileNoAndFirmId(@Param("mobileNo") String mobileNo, @Param("firmId") UUID firmId);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.username = :username AND u.userType = :userType")
    boolean existsByUsernameAndUserType(@Param("username") String username, @Param("userType") UserType userType);

    @Query("SELECT u.permissionVersion FROM User u WHERE u.id = :userId")
    Integer findPermissionVersionById(@Param("userId") UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.permissionVersion = u.permissionVersion + 1 WHERE u.id = :userId")
    void incrementPermissionVersion(@Param("userId") UUID userId);

    @Query("SELECT u.id FROM User u WHERE u.role.id = :roleId")
    List<UUID> findUserIdsByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT u FROM User u WHERE u.role.id = :roleId")
    List<User> findByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT u FROM User u WHERE u.firm.id = :firmId AND u.role.id = :roleId")
    List<User> findByFirmIdAndRoleId(@Param("firmId") UUID firmId, @Param("roleId") UUID roleId);

    @Query("SELECT u FROM User u WHERE u.userType = :userType")
    List<User> findByUserType(@Param("userType") UserType userType);

    @Query("SELECT u FROM User u WHERE u.role.roleCode = 'FIRM_ADMIN' AND u.firm IS NOT NULL")
    List<User> findAllFirmAdmins();

    // Or scoped to one firm:
    @Query("SELECT u FROM User u WHERE u.role.roleCode = 'FIRM_ADMIN' AND u.firm.id = :firmId")
    List<User> findFirmAdminsByFirmId(@Param("firmId") UUID firmId);

    // ── For employee/client code generation
    @Query("SELECT COUNT(u) FROM User u WHERE u.firm.id = :firmId AND u.userType = :userType")
    long countByFirmIdAndUserType(@Param("firmId") UUID firmId, @Param("userType") UserType userType);

    @Query("SELECT u FROM User u WHERE u.firm.id = :firmId AND u.userType = :userType")
    Page<User> findByFirmIdAndUserTypePaged(@Param("firmId") UUID firmId,
                                            @Param("userType") UserType userType,
                                            Pageable pageable);

    boolean existsByUsername(@Param("username") String username);
}