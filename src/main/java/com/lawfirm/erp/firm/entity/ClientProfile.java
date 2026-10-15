package com.lawfirm.erp.firm.entity;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "client_code", nullable = false, length = 30, updatable = false)
    private String clientCode;

    @Column(name = "company_name", length = 150)
    private String companyName;

    @Column(length = 200)
    private String address;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Column(name = "contact_person_phone", length = 15)
    private String contactPersonPhone;

    @Column(columnDefinition = "TEXT")
    private String notes;
}