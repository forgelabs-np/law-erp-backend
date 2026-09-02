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

import java.time.LocalDateTime;
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

    /**
     * All users with their role and firm eagerly loaded (join fetch — no N+1),
     * optionally filtered for the super-admin "users with roles" view.
     * Every filter is nullable — a null filter is ignored.
     * NOTE: params are CAST to string so Hibernate binds them as varchar even
     * when null — without the CAST, null params are sent as bytea by pgjdbc
     * and "lower(bytea)" blows up on Postgres. Only params inside LOWER/CONCAT need the CAST —
     * the userType enum and plain-equality params bind fine without it (do NOT re-add CAST there,
     * it breaks enum binding).
     */
    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.role
            LEFT JOIN FETCH u.firm
            WHERE (:userType IS NULL OR u.userType = :userType)
              AND (:search IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
              AND (:firmCode IS NULL OR u.firm.lawFirmCode = CAST(:firmCode AS string))
            ORDER BY u.createdAt DESC
            """)
    List<User> findAllWithRoleAndFirm(@Param("userType") UserType userType,
                                      @Param("search") String search,
                                      @Param("firmCode") String firmCode);

    /**
     * Paginated version — no JOIN FETCH (Spring Data manages pagination via count query).
     * Role and firm are lazy-loaded; use EntityGraph or handle in service.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:userType IS NULL OR u.userType = :userType)
              AND (:search IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
              AND (:firmCode IS NULL OR u.firm.lawFirmCode = CAST(:firmCode AS string))
            ORDER BY u.createdAt DESC
            """)
    Page<User> findAllWithRoleAndFirmPaged(@Param("userType") UserType userType,
                                           @Param("search") String search,
                                           @Param("firmCode") String firmCode,
                                           Pageable pageable);

    @Query("SELECT u.role.id as roleId, COUNT(u) as cnt FROM User u WHERE u.firm.id = :firmId GROUP BY u.role.id")
    List<Object[]> countUsersByRoleIds(@Param("firmId") UUID firmId);

    @Query("SELECT u.role.id as roleId, u.fullName as fullName FROM User u WHERE u.firm.id = :firmId AND u.role.id IN :roleIds")
    List<Object[]> findUserNamesByRoleIds(@Param("firmId") UUID firmId, @Param("roleIds") List<UUID> roleIds);

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

    /** Count all users in a firm — avoids loading all users into memory. */
    @Query("SELECT COUNT(u) FROM User u WHERE u.firm.id = :firmId")
    long countByFirmId(@Param("firmId") UUID firmId);

    /** Count active users in a firm. */
    @Query("SELECT COUNT(u) FROM User u WHERE u.firm.id = :firmId AND u.active = true")
    long countActiveByFirmId(@Param("firmId") UUID firmId);

    /** Count users by role code in a firm — avoids N+1 in role listing. */
    @Query("SELECT COUNT(u) FROM User u WHERE u.firm.id = :firmId AND u.role.roleCode = :roleCode")
    long countByFirmIdAndRoleCode(@Param("firmId") UUID firmId, @Param("roleCode") String roleCode);

    /** Count users by user type. */
    @Query("SELECT COUNT(u) FROM User u WHERE u.userType = :userType")
    long countByUserType(@Param("userType") UserType userType);

    // ── Trend queries ─────────────────────────────────────────────────────

    /** Daily new user counts with active/inactive split, grouped by date. */
    @Query("SELECT FUNCTION('DATE', u.createdAt) as d, COUNT(u) as total, " +
           "SUM(CASE WHEN u.active = true THEN 1 ELSE 0 END) as active, " +
           "SUM(CASE WHEN u.active = false THEN 1 ELSE 0 END) as inactive, " +
           "SUM(CASE WHEN u.userType = 'CLIENT' THEN 1 ELSE 0 END) as clients " +
           "FROM User u WHERE u.createdAt >= :from AND u.createdAt < :to " +
           "GROUP BY FUNCTION('DATE', u.createdAt) ORDER BY d ASC")
    List<Object[]> countDailyByDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Firm-scoped daily new user counts. */
    @Query("SELECT FUNCTION('DATE', u.createdAt) as d, COUNT(u) as total, " +
           "SUM(CASE WHEN u.active = true THEN 1 ELSE 0 END) as active, " +
           "SUM(CASE WHEN u.active = false THEN 1 ELSE 0 END) as inactive, " +
           "SUM(CASE WHEN u.userType = 'CLIENT' THEN 1 ELSE 0 END) as clients " +
           "FROM User u WHERE u.firm.id = :firmId AND u.createdAt >= :from AND u.createdAt < :to " +
           "GROUP BY FUNCTION('DATE', u.createdAt) ORDER BY d ASC")
    List<Object[]> countDailyByFirmIdAndDateRange(@Param("firmId") UUID firmId,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);
}