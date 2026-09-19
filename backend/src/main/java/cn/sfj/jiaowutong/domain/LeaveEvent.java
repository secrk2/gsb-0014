package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 请假审批流水留痕：一张请假单从提交、两级审批（通过/退回）、重提、撤回、销假
 * 到系统逾假判定的每一步都落一条，审批意见、操作人、时间全程可回溯。
 * 退回后重提不覆盖历史记录，保证“退回能重提并留痕”。
 */
@Entity
@Table(name = "leave_event", indexes = {
        @Index(name = "idx_leave_event_leave", columnList = "leave_id,occurred_at")
})
public class LeaveEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_id", nullable = false)
    private Long leaveId;

    /**
     * 动作类型：
     * SUBMITTED 提交 / RESUBMITTED 退回后重提 / OFFICE_APPROVED 司法所初审通过 /
     * OFFICE_RETURNED 司法所退回 / BUREAU_APPROVED 区局复核通过 / BUREAU_RETURNED 区局退回 /
     * WITHDRAWN 对象撤回 / RETURN_CHECKIN 对象销假 / SYSTEM_OVERDUE 系统判定逾假未归
     */
    @Column(nullable = false, length = 24)
    private String action;

    /** 该步发生时请假单所处状态 */
    @Column(name = "result_status", nullable = false, length = 20)
    private String resultStatus;

    /** 操作人账号 id（系统自动判定为 0） */
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operatorName;

    /** 审批意见 / 退回理由 / 销假说明（提交时为申请事由摘要，可空） */
    @Column(length = 512)
    private String comment;

    @Column(nullable = false)
    private Instant occurredAt;

    public LeaveEvent() {
    }

    public LeaveEvent(Long leaveId, String action, LeaveApplication.Status resultStatus,
                      Long operatorId, String operatorName, String comment, Instant occurredAt) {
        this.leaveId = leaveId;
        this.action = action;
        this.resultStatus = resultStatus.name();
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.comment = comment;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getLeaveId() { return leaveId; }
    public String getAction() { return action; }
    public String getResultStatus() { return resultStatus; }
    public Long getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public String getComment() { return comment; }
    public Instant getOccurredAt() { return occurredAt; }
}
