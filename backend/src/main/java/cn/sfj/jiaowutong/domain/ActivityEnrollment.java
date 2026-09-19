package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 公益活动报名：对象在手机端对已发布活动报名。同一对象对同一活动只能报名一次（唯一约束）。
 * 报名状态：ENROLLED 已报名 / CANCELLED 已取消（活动开始前可自行取消）。
 */
@Entity
@Table(name = "activity_enrollment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_enrollment_activity_offender",
                columnNames = {"activity_id", "offender_id"})
}, indexes = {
        @Index(name = "idx_enrollment_offender", columnList = "offender_id"),
        @Index(name = "idx_enrollment_activity", columnList = "activity_id")
})
public class ActivityEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id")
    private PublicActivity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offender_id")
    private CorrectionObject offender;

    @Column(nullable = false, length = 16)
    private String status = "ENROLLED";

    @Column(nullable = false)
    private Instant enrolledAt;

    private Instant cancelledAt;

    public ActivityEnrollment() {
    }

    public ActivityEnrollment(PublicActivity activity, CorrectionObject offender, Instant enrolledAt) {
        this.activity = activity;
        this.offender = offender;
        this.enrolledAt = enrolledAt;
    }

    public Long getId() { return id; }
    public PublicActivity getActivity() { return activity; }
    public CorrectionObject getOffender() { return offender; }
    public String getStatus() { return status; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Instant getCancelledAt() { return cancelledAt; }

    public void setStatus(String status) { this.status = status; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}
