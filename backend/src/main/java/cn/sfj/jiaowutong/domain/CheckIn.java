package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.Instant;

/**
 * 日常报到记录。
 */
@Entity
@Table(name = "check_in", indexes = {
        @Index(name = "idx_checkin_offender_day", columnList = "offender_id,check_date")
})
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offender_id")
    private CorrectionObject offender;

    @Column(nullable = false)
    private LocalDate checkDate;

    @Column(nullable = false)
    private Instant checkedAt;

    /** 报到方式：APP（对象手机定位报到）/ IN_PERSON（月度当面报到，干警批量登记） */
    @Column(nullable = false, length = 16)
    private String method;

    /** IN_PERSON 月度报到：登记干警账号 id / 姓名（APP 自助报到为空） */
    @Column(name = "registered_by_id")
    private Long registeredById;

    @Column(name = "registered_by_name", length = 64)
    private String registeredByName;

    private Double lat;
    private Double lng;

    /** 报到时是否在围栏内 */
    @Column(nullable = false)
    private Boolean insideFence;

    /** 报到定位虽在围栏外，但处于已批准请假窗口内（准假外出，不算异常） */
    @Column(name = "leave_authorized", nullable = false)
    private Boolean leaveAuthorized = false;

    public CheckIn() {
    }

    public CheckIn(CorrectionObject offender, LocalDate checkDate, Instant checkedAt,
                   String method, Double lat, Double lng, Boolean insideFence) {
        this.offender = offender;
        this.checkDate = checkDate;
        this.checkedAt = checkedAt;
        this.method = method;
        this.lat = lat;
        this.lng = lng;
        this.insideFence = insideFence;
    }

    public Long getId() { return id; }
    public CorrectionObject getOffender() { return offender; }
    public LocalDate getCheckDate() { return checkDate; }
    public Instant getCheckedAt() { return checkedAt; }
    public String getMethod() { return method; }
    public Long getRegisteredById() { return registeredById; }
    public String getRegisteredByName() { return registeredByName; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public Boolean getInsideFence() { return insideFence; }
    public Boolean getLeaveAuthorized() { return leaveAuthorized; }

    public void setRegisteredById(Long registeredById) { this.registeredById = registeredById; }
    public void setRegisteredByName(String registeredByName) { this.registeredByName = registeredByName; }
    public void setLeaveAuthorized(Boolean leaveAuthorized) { this.leaveAuthorized = leaveAuthorized; }
}
