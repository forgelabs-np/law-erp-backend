package com.lawfirm.erp.firm.entity;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Employee-specific profile data — 1:1 with User.
 *
 * User table = authentication (who can log in, what role, what firm).
 * EmployeeProfile = professional data (designation, bar council, etc.).
 *
 * This separation keeps User lean and lets EmployeeProfile grow
 * independently without touching auth logic.
 *
 * employeeCode format: {FIRMCODE}-EMP-{SEQUENCE}
 * e.g. APEX_LAW-EMP-0001
 * Generated once at creation, never changes (immutable reference).
 */
@Entity
@Table(
        name = "employee_profiles",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id"}),
                @UniqueConstraint(columnNames = {"employee_code"})
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmployeeProfile extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ── Identity ────────────────────────────────────────────────────────────
    @Column(name = "employee_code", nullable = false, length = 30, updatable = false)
    private String employeeCode;   // APEX_LAW-EMP-0001 — immutable

    // ── Professional info ────────────────────────────────────────────────────
    @Column(length = 100)
    private String designation;    // "Senior Advocate", "Junior Advocate", "Paralegal"

    @Column(name = "bar_council_no", length = 50)
    private String barCouncilNo;   // e.g. "KTM-12345" — for advocates only

    @Column(length = 100)
    private String specialization; // "Criminal Law", "Civil Law", "Family Law"

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    // ── Emergency contact ────────────────────────────────────────────────────
    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 15)
    private String emergencyContactPhone;

    // ── Notes ────────────────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String notes;          // Internal notes by firm admin
}