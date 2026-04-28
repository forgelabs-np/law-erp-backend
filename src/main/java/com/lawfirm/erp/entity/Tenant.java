package com.lawfirm.erp.entity;

import com.lawfirm.erp.enums.TenantType;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "tenants")
@EntityListeners(AuditingEntityListener.class)
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String tenantId;

    @Column(unique = true, nullable = false)
    private String subdomain;

    @Column(unique = true, nullable = false)
    private String schemaName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantType tenantType;

    @Column(nullable = false)
    private String organizationName;

    private String email;
    private String phone;
    private String address;
    private String panNumber;
    private String barCouncilNumber;

    private String status = "ACTIVE";

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        tenantId = UUID.randomUUID().toString();
    }
}