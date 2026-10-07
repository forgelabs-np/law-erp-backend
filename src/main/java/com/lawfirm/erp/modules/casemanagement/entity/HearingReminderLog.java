package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hearing_reminder_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hearing_reminder",
                columnNames = {"courtEventId", "recipientType", "recipientEmail", "scheduledDate"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HearingReminderLog extends AuditableEntity {

    public enum RecipientType { CLIENT, ADVOCATE }

    public enum Status { SENT, FAILED }

    @Column(nullable = false)
    private UUID courtEventId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private RecipientType recipientType;

    @Column(nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private Status status = Status.SENT;
}
