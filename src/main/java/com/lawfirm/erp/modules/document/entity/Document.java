package com.lawfirm.erp.modules.document.entity;

import com.lawfirm.erp.modules.document.enums.DocumentStatus;
import com.lawfirm.erp.modules.document.enums.DocumentVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One stored document, bound to exactly one owner: a case ({@code matterId}) or a project
 * ({@code projectId}). The XOR between the two is enforced by the service and by a database
 * check constraint.
 *
 * <p>Two nullable foreign keys rather than a polymorphic {@code ownerType}/{@code ownerId}
 * pair, because both sides are queried and indexed independently and a discriminator column
 * could not be joined.
 *
 * <p>{@code status} — not an {@code isActive} flag — is the single lifecycle authority.
 */
@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_documents_firm_matter", columnList = "firm_id, matter_id, status"),
        @Index(name = "idx_documents_firm_project", columnList = "firm_id, project_id, status"),
        @Index(name = "idx_documents_firm_created", columnList = "firm_id, created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stable external identity. Needed because {@code audit_logs.entity_id} is a UUID, so the
     * numeric primary key cannot be the reference the audit trail stores.
     */
    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "firm_id", nullable = false)
    private UUID firmId;

    /** Set when the document belongs to a case. Exactly one of matterId/projectId is present. */
    @Column(name = "matter_id")
    private UUID matterId;

    /** Set when the document belongs to a project. */
    @Column(name = "project_id")
    private UUID projectId;

    /** Optional tag for the specific court instance inside the matter. */
    @Column(name = "court_case_id")
    private UUID courtCaseId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "extension", nullable = false, length = 10)
    private String extension;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Object key in the storage bucket. Authoritative — the readable path is a convenience. */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "etag", length = 64)
    private String etag;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 10)
    private DocumentVisibility visibility = DocumentVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.ACTIVE;

    @Column(name = "uploaded_by_user_id")
    private UUID uploadedByUserId;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "archived_by_user_id")
    private UUID archivedByUserId;

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

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
