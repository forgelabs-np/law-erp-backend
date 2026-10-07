package com.lawfirm.erp.firm.entity;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

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

    @Column(name = "employee_code", nullable = false, length = 30, updatable = false)
    private String employeeCode;

    @Column(length = 100)
    private String designation;

    @Column(name = "bar_council_no", length = 50)
    private String barCouncilNo;

    @Column(length = 100)
    private String specialization;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 15)
    private String emergencyContactPhone;

    @Column(columnDefinition = "TEXT")
    private String notes;
}