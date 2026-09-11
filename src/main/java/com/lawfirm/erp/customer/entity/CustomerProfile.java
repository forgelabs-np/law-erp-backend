package com.lawfirm.erp.customer.entity;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.firm.entity.Firm;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "customer_profiles")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomerProfile extends ActiveAuditableEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id", nullable = false)
    private Firm firm;

    private String address;
    private String nationalId;
    private String occupation;

    @Column(name = "portal_pin")
    private String portalPin;
}