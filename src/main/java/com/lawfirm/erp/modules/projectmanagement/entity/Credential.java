package com.lawfirm.erp.modules.projectmanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_credentials", indexes = {
        @Index(name = "idx_cred_project", columnList = "projectId")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(name = "site_name", nullable = false, length = 150)
    private String siteName;

    @Column(name = "site_type", length = 50)
    private String siteType;

    @Column(name = "site_url", length = 500)
    private String siteUrl;

    @Column(name = "username_or_email", nullable = false, length = 200)
    private String usernameOrEmail;

    @Column(name = "encrypted_password", nullable = false, columnDefinition = "TEXT")
    private String encryptedPassword;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Column(name = "contact_phone", length = 15)
    private String contactPhone;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;
}
