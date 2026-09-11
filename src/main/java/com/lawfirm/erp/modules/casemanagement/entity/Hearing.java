package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.casemanagement.enums.HearingStatus;
import com.lawfirm.erp.modules.casemanagement.enums.HearingType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "hearings", indexes = {
        @Index(name = "idx_hearings_case", columnList = "caseId"),
        @Index(name = "idx_hearings_date", columnList = "firmId, date DESC"),
        @Index(name = "idx_hearings_advocate_date", columnList = "advocateId, date DESC"),
        @Index(name = "idx_hearings_status", columnList = "firmId, status, date DESC")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Hearing extends ActiveAuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    @Column(nullable = false)
    private UUID caseId;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate date;

    private LocalTime time;

    private LocalTime endTime;

    @Column(length = 50)
    private String courtRoom;

    @Column(length = 100)
    private String judgeName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private HearingType hearingType;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private HearingStatus status;

    @Column(columnDefinition = "TEXT")
    private String outcome;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String attendees;

    private UUID advocateId;
}
