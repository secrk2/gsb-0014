package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 违规/越界事件，作战台红点来源。
 * 类型：GEOFENCE_BREACH 越界 / ABSENT 未按日报到 / STATUS_VIOLATION 训诫等违规
 */
@Entity
@Table(name = "violation_event", indexes = {
        @Index(name = "idx_violation_office_unread", columnList = "office_id,read_flag,event_time")
})
public class ViolationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offender_id")
    private CorrectionObject offender;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 256)
    private String detail;

    @Column(nullable = false)
    private Instant eventTime;

    /** 红点是否已读/处置 */
    @Column(name = "read_flag", nullable = false)
    private Boolean readFlag = false;

    public ViolationEvent() {
    }

    public ViolationEvent(CorrectionObject offender, String type, String detail, Instant eventTime) {
        this.offender = offender;
        this.officeId = offender.getOffice().getId();
        this.type = type;
        this.detail = detail;
        this.eventTime = eventTime;
    }

    public Long getId() { return id; }
    public CorrectionObject getOffender() { return offender; }
    public Long getOfficeId() { return officeId; }
    public String getType() { return type; }
    public String getDetail() { return detail; }
    public Instant getEventTime() { return eventTime; }
    public Boolean getReadFlag() { return readFlag; }
    public void setReadFlag(Boolean readFlag) { this.readFlag = readFlag; }
}
