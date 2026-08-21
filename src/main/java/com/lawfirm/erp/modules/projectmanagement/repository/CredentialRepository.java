package com.lawfirm.erp.modules.projectmanagement.repository;

import com.lawfirm.erp.modules.projectmanagement.entity.Credential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, Long> {

    List<Credential> findByProjectIdAndActive(UUID projectId, boolean active);

    long countByProjectIdAndActive(UUID projectId, boolean active);
}
