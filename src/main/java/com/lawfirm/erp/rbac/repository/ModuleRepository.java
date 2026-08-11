package com.lawfirm.erp.rbac.repository;

import com.lawfirm.erp.rbac.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModuleRepository extends JpaRepository<Module, UUID> {
    Optional<Module> findByCode(String code);
    Optional<Module> findByName(String name);
    boolean existsByCode(String code);
    boolean existsByName(String name);

    @Query("SELECT m FROM Module m ORDER BY m.displayOrder ASC")
    List<Module> findAllOrderByDisplayOrder();

    /**
     * All modules ordered by display order, with the parent join-fetched
     * so the /me module tree can nest sub-modules without N+1 lookups.
     */
    @Query("SELECT m FROM Module m LEFT JOIN FETCH m.parent ORDER BY m.displayOrder ASC")
    List<Module> findAllWithParentOrderByDisplayOrder();

    @Query("SELECT m FROM Module m WHERE m.active = true ORDER BY m.displayOrder ASC")
    List<Module> findActiveModules();
}