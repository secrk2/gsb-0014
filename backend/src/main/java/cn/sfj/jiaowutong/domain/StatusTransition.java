package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 档案状态流转记录（状态机留痕）。
 */
@Entity
@Table(name = "status_transition")
public class StatusTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long offenderId;

    /** 首条入矫建档记录无前态，允许为空 */
    @Column(length = 20)
    private String fromStatus;

    @Column(nullable = false, length = 20)
    private String toStatus;

    /** 操作人账号 id */
    @Column(nullable = false)
    private Long operatorId;

    @Column(nullable = false, length = 64)
    private String operatorName;

    @Column(length = 256)
    private String reason;

    @Column(nullable = false)
    private Instant operatedAt;

    public StatusTransition() {
    }

    public StatusTransition(Long offenderId, CorrectionStatus from, CorrectionStatus to,
                            Long operatorId, String operatorName, String reason) {
        this.offenderId = offenderId;
        this.fromStatus = from == null ? null : from.name();
        this.toStatus = to.name();
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.reason = reason;
        this.operatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOffenderId() { return offenderId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public Long getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public String getReason() { return reason; }
    public Instant getOperatedAt() { return operatedAt; }
}
