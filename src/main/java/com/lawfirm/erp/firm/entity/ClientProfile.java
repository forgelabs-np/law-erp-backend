package com.lawfirm.erp.firm.entity;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Client-specific profile data — 1:1 with User.
 *
 * clientCode format: {FIRMCODE}-CLI-{SEQUENCE}
 * e.g. APEX_LAW-CLI-0001
 * Generated once at creation, immutable.
 */
@Entity
@Table(
        name = "client_profiles",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id"}),
                @UniqueConstraint(columnNames = {"client_code"})
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClientProfile extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ── Identity ────────────────────────────────────────────────────────────
    @Column(name = "client_code", nullable = false, length = 30, updatable = false)
    private String clientCode;     // APEX_LAW-CLI-0001 — immutable

    // ── Client details ───────────────────────────────────────────────────────
    @Column(name = "company_name", length = 150)
    private String companyName;    // If the client is a company

    @Column(length = 200)
    private String address;

    @Column(name = "pan_number", length = 20)
    private String panNumber;      // PAN/TIN for billing

    @Column(name = "contact_person", length = 100)
    private String contactPerson;  // Primary contact if company

    @Column(name = "contact_person_phone", length = 15)
    private String contactPersonPhone;

    @Column(columnDefinition = "TEXT")
    private String notes;          // Internal notes by firm admin
}