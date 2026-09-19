package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 社区矫正对象档案。
 */
@Entity
@Table(name = "correction_object", indexes = {
        @Index(name = "idx_obj_office", columnList = "office_id"),
        @Index(name = "idx_obj_status", columnList = "status")
})
public class CorrectionObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 矫正编号，唯一，如 JWT2026001 */
    @Column(nullable = false, unique = true, length = 32)
    private String correctionNo;

    /** 真实姓名（敏感信息：列表默认不返回，需二次确认+理由后查看并留痕） */
    @Column(nullable = false, length = 64)
    private String fullName;

    /** 脱敏展示名：姓首字母+编号，如 Z-JWT26001，由服务层计算，不直接暴露 fullName */
    @Column(nullable = false, length = 64)
    private String maskedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CorrectionStatus status = CorrectionStatus.INTAKE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "office_id")
    private JudicialOffice office;

    @Column(length = 32)
    private String idCardTail;

    /** 罪名 */
    @Column(length = 64)
    private String charge;

    /** 矫正期限起 */
    private LocalDate startDate;

    /** 矫正期限止 */
    private LocalDate endDate;

    /** 联系电话 */
    @Column(length = 20)
    private String phone;

    /** 规定报到日：MONDAY..SUNDAY（按对象所在司法所时区判定） */
    @Column(length = 16)
    private String reportDay;

    /** 最近一次有效定位时间（UTC，由轨迹上报更新） */
    @Column(name = "last_location_at")
    private Instant lastLocationAt;

    private Double lastLat;
    private Double lastLng;

    /** 最近一次定位是否在活动范围内 */
    @Column(name = "last_inside_fence")
    private Boolean lastInsideFence;

    /** 最近一次定位是否处于排程禁区 */
    @Column(name = "last_forbidden")
    private Boolean lastForbidden;

    /** 最近一次定位虽越出活动范围，但处于已批准请假的假期窗口内（准假外出，不算越界） */
    @Column(name = "last_leave_authorized")
    private Boolean lastLeaveAuthorized;

    /** 最近一次设备状态：电量 0-100 / 信号 0-4 / 是否佩戴 */
    private Integer lastBattery;
    private Integer lastSignal;
    private Boolean lastWorn;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getCorrectionNo() { return correctionNo; }
    public String getFullName() { return fullName; }
    public String getMaskedName() { return maskedName; }
    public CorrectionStatus getStatus() { return status; }
    public JudicialOffice getOffice() { return office; }
    public String getIdCardTail() { return idCardTail; }
    public String getCharge() { return charge; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getPhone() { return phone; }
    public String getReportDay() { return reportDay; }
    public Instant getLastLocationAt() { return lastLocationAt; }
    public Double getLastLat() { return lastLat; }
    public Double getLastLng() { return lastLng; }
    public Boolean getLastInsideFence() { return lastInsideFence; }
    public Boolean getLastForbidden() { return lastForbidden; }
    public Boolean getLastLeaveAuthorized() { return lastLeaveAuthorized; }
    public Integer getLastBattery() { return lastBattery; }
    public Integer getLastSignal() { return lastSignal; }
    public Boolean getLastWorn() { return lastWorn; }
    public Instant getCreatedAt() { return createdAt; }

    public void setCorrectionNo(String correctionNo) { this.correctionNo = correctionNo; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setMaskedName(String maskedName) { this.maskedName = maskedName; }
    public void setStatus(CorrectionStatus status) { this.status = status; }
    public void setOffice(JudicialOffice office) { this.office = office; }
    public void setIdCardTail(String idCardTail) { this.idCardTail = idCardTail; }
    public void setCharge(String charge) { this.charge = charge; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setReportDay(String reportDay) { this.reportDay = reportDay; }
    public void setLastLocationAt(Instant lastLocationAt) { this.lastLocationAt = lastLocationAt; }
    public void setLastLat(Double lastLat) { this.lastLat = lastLat; }
    public void setLastLng(Double lastLng) { this.lastLng = lastLng; }
    public void setLastInsideFence(Boolean lastInsideFence) { this.lastInsideFence = lastInsideFence; }
    public void setLastForbidden(Boolean lastForbidden) { this.lastForbidden = lastForbidden; }
    public void setLastLeaveAuthorized(Boolean lastLeaveAuthorized) { this.lastLeaveAuthorized = lastLeaveAuthorized; }
    public void setLastBattery(Integer lastBattery) { this.lastBattery = lastBattery; }
    public void setLastSignal(Integer lastSignal) { this.lastSignal = lastSignal; }
    public void setLastWorn(Boolean lastWorn) { this.lastWorn = lastWorn; }
}
