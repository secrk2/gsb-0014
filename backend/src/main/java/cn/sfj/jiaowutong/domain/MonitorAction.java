package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 定位监控操作留痕：清除轨迹、越界落点二次核实等敏感动作必须记录操作人、原因与结论。
 * 它同时支撑前端区分“该对象从无轨迹”与“轨迹曾存在但已被清除”两种空态。
 */
@Entity
@Table(name = "monitor_action", indexes = {
        @Index(name = "idx_monitor_action_obj", columnList = "offender_id,created_at")
})
public class MonitorAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offender_id", nullable = false)
    private Long offenderId;

    /** CLEAR_TRACKS 清除轨迹 / VERIFY_POINT 越界落点核实 */
    @Column(nullable = false, length = 32)
    private String action;

    @Column(nullable = false)
    private Long operatorId;

    @Column(nullable = false, length = 64)
    private String operatorName;

    @Column(nullable = false, length = 256)
    private String reason;

    /** 结论（如核实结果 REALLY_BREACH 确认越界 / FALSE_ALARM 误报排除），可空 */
    @Column(length = 32)
    private String conclusion;

    /** 越界落点核实：被核实的轨迹点 id */
    private Long pointId;

    /** 清除轨迹：本次清除的有效点数 */
    private Integer clearedCount;

    /** 关联上下文备注 */
    @Column(length = 256)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    public MonitorAction() {
    }

    public MonitorAction(Long offenderId, String action, Long operatorId, String operatorName,
                         String reason, String conclusion, Long pointId, Integer clearedCount, String detail) {
        this.offenderId = offenderId;
        this.action = action;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.reason = reason;
        this.conclusion = conclusion;
        this.pointId = pointId;
        this.clearedCount = clearedCount;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOffenderId() { return offenderId; }
    public String getAction() { return action; }
    public Long getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public String getReason() { return reason; }
    public String getConclusion() { return conclusion; }
    public Long getPointId() { return pointId; }
    public Integer getClearedCount() { return clearedCount; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
