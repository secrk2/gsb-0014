package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 请假申请（请销假单）。两级审批：司法所初审 → 区司法局复核。
 *
 * <p>状态流转：
 * <pre>
 *  (对象提交) PENDING_OFFICE
 *     ├─ 司法所初审通过 → PENDING_BUREAU
 *     │      ├─ 区局复核通过 → APPROVED（准假，对象状态置「请假外出」）
 *     │      │      ├─ 对象按期/提前销假 → COMPLETED
 *     │      │      └─ 假期结束仍未销假 → OVERDUE（系统定时任务自动生成违规红点）
 *     │      │             └─ 对象事后销假 → COMPLETED（违规红点保留）
 *     │      └─ 区局复核退回 → RETURNED（对象修改后可重提，重新走两级）
 *     └─ 司法所初审退回 → RETURNED
 *  RETURNED ─ 重新提交 → PENDING_OFFICE
 *  审批通过前对象可撤回 → CANCELLED
 * </pre>
 *
 * 每次提交/通过/退回/重提/撤回/销假/系统逾期判定都落 {@link LeaveEvent} 流水留痕。
 *
 * <p>定位联动：{@link #status} 为 APPROVED 且定位采集时刻落在 [startTime, endTime] 内，
 * 视为“准假外出期间”，越过司法所活动范围围栏不算越界、不产生越界红点；
 * 但法定禁区不因请假而解禁，禁区闯入照常预警。
 */
@Entity
@Table(name = "leave_application", indexes = {
        @Index(name = "idx_leave_offender", columnList = "offender_id,status"),
        @Index(name = "idx_leave_office_status", columnList = "office_id,status"),
        @Index(name = "idx_leave_status_end", columnList = "status,end_time")
})
public class LeaveApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offender_id")
    private CorrectionObject offender;

    /** 冗余司法所 id，便于数据范围过滤与审批列表查询 */
    @Column(name = "office_id", nullable = false)
    private Long officeId;

    /** 请假类型：PERSONAL 事假 / SICK 病假 / OTHER 其他 */
    @Column(nullable = false, length = 16)
    private String leaveType;

    /** 请假事由 */
    @Column(nullable = false, length = 512)
    private String reason;

    /** 外出目的地 */
    @Column(nullable = false, length = 256)
    private String destination;

    /** 假期开始时刻（UTC） */
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    /** 假期截止时刻（UTC）；超过该时刻仍未销假由系统升为逾假违规 */
    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    /** 司法所初审通过时刻 */
    @Column(name = "office_approved_at")
    private Instant officeApprovedAt;

    /** 区局复核通过（终批准假）时刻 */
    @Column(name = "bureau_approved_at")
    private Instant bureauApprovedAt;

    /** 最近一次被退回时刻 */
    @Column(name = "returned_at")
    private Instant returnedAt;

    /** 最近一次重新提交时刻 */
    @Column(name = "resubmitted_at")
    private Instant resubmittedAt;

    /** 实际销假时刻 */
    @Column(name = "actual_return_at")
    private Instant actualReturnAt;

    /** 是否已生成逾假未归违规（幂等标记，状态置 OVERDUE 即已处理） */
    @Column(name = "overdue_flag", nullable = false)
    private Boolean overdueFlag = false;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public enum Status {
        PENDING_OFFICE("待司法所初审"),
        PENDING_BUREAU("待区局复核"),
        RETURNED("已退回·待修改重提"),
        APPROVED("已批准·假期中"),
        OVERDUE("逾假未归"),
        COMPLETED("已销假"),
        CANCELLED("已撤回");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public LeaveApplication() {
    }

    public LeaveApplication(CorrectionObject offender, String leaveType, String reason,
                            String destination, Instant startTime, Instant endTime,
                            Status status, Instant submittedAt) {
        this.offender = offender;
        this.officeId = offender.getOffice().getId();
        this.leaveType = leaveType;
        this.reason = reason;
        this.destination = destination;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.submittedAt = submittedAt;
        this.createdAt = submittedAt;
    }

    public Long getId() { return id; }
    public CorrectionObject getOffender() { return offender; }
    public Long getOfficeId() { return officeId; }
    public String getLeaveType() { return leaveType; }
    public String getReason() { return reason; }
    public String getDestination() { return destination; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public Status getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getOfficeApprovedAt() { return officeApprovedAt; }
    public Instant getBureauApprovedAt() { return bureauApprovedAt; }
    public Instant getReturnedAt() { return returnedAt; }
    public Instant getResubmittedAt() { return resubmittedAt; }
    public Instant getActualReturnAt() { return actualReturnAt; }
    public Boolean getOverdueFlag() { return overdueFlag; }
    public Instant getCreatedAt() { return createdAt; }

    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }
    public void setReason(String reason) { this.reason = reason; }
    public void setDestination(String destination) { this.destination = destination; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public void setStatus(Status status) { this.status = status; }
    public void setOfficeApprovedAt(Instant officeApprovedAt) { this.officeApprovedAt = officeApprovedAt; }
    public void setBureauApprovedAt(Instant bureauApprovedAt) { this.bureauApprovedAt = bureauApprovedAt; }
    public void setReturnedAt(Instant returnedAt) { this.returnedAt = returnedAt; }
    public void setResubmittedAt(Instant resubmittedAt) { this.resubmittedAt = resubmittedAt; }
    public void setActualReturnAt(Instant actualReturnAt) { this.actualReturnAt = actualReturnAt; }
    public void setOverdueFlag(Boolean overdueFlag) { this.overdueFlag = overdueFlag; }
}
