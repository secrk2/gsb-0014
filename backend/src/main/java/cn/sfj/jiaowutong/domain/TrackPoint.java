package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 轨迹点（UTC 时刻，与设备/服务器所在时区无关）。
 * clientPointId 为腕表生成的幂等键（UUID），离线缓存恢复后重放不会产生重复点。
 * pointTime 为 GPS 采集瞬间（UTC）；receivedAt 为服务端入库瞬间（UTC）。
 */
@Entity
@Table(name = "track_point", uniqueConstraints = {
        @UniqueConstraint(name = "uk_track_client_point", columnNames = {"offender_id", "client_point_id"})
}, indexes = {
        @Index(name = "idx_track_offender_time", columnList = "offender_id,point_time")
})
public class TrackPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offender_id")
    private CorrectionObject offender;

    /** 客户端生成的幂等 ID */
    @Column(name = "client_point_id", nullable = false, length = 64)
    private String clientPointId;

    /** 定位采集时间（GPS 瞬间，UTC），离线期间为过去时刻 */
    @Column(name = "point_time", nullable = false)
    private Instant pointTime;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    /** 采集该点时是否处于离线：true=离线缓存点，false=实时点 */
    @Column(name = "offline_captured", nullable = false)
    private Boolean offlineCaptured;

    /** 服务端入库时间（UTC） */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** 该点是否越出活动范围（圆形兜底或 ALLOW 多边形之外） */
    @Column(name = "outside_fence", nullable = false)
    private Boolean outsideFence;

    /** 该点是否落入按对象时区排程的禁区 */
    @Column(name = "forbidden_zone", nullable = false)
    private Boolean forbiddenZone = false;

    /**
     * 该点虽几何上越出活动范围，但处于已批准请假单的假期时段内：
     * 属“准假外出”，不算越界、不产生越界红点（监控轨迹以请假色单独区分）。
     */
    @Column(name = "leave_authorized", nullable = false)
    private Boolean leaveAuthorized = false;

    /** 腕表电量百分比 0-100（无上报时为空） */
    private Integer battery;

    /** 信号强度 0-4（无上报时为空） */
    private Integer signal;

    /** 腕表是否佩戴中（脱戴检测；无上报时为空） */
    private Boolean worn;

    /**
     * 合并/质检标记：
     * ACCEPTED 正常入库参与轨迹；DUPLICATE 幂等去重；
     * DRIFT_DISCARDED 连续两点跳变速度超合理上限，判为 GPS 漂移丢弃（不连线、不更新锚点、不触发越界）。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IngestResult result = IngestResult.ACCEPTED;

    public enum IngestResult { ACCEPTED, DUPLICATE, DRIFT_DISCARDED }

    public TrackPoint() {
    }

    public TrackPoint(CorrectionObject offender, String clientPointId, Instant pointTime,
                      Double lat, Double lng, Boolean offlineCaptured,
                      Instant receivedAt, Boolean outsideFence, IngestResult result) {
        this(offender, clientPointId, pointTime, lat, lng, offlineCaptured,
                receivedAt, outsideFence, false, null, null, null, result);
    }

    public TrackPoint(CorrectionObject offender, String clientPointId, Instant pointTime,
                      Double lat, Double lng, Boolean offlineCaptured,
                      Instant receivedAt, Boolean outsideFence, Boolean forbiddenZone,
                      Integer battery, Integer signal, Boolean worn, IngestResult result) {
        this.offender = offender;
        this.clientPointId = clientPointId;
        this.pointTime = pointTime;
        this.lat = lat;
        this.lng = lng;
        this.offlineCaptured = offlineCaptured;
        this.receivedAt = receivedAt;
        this.outsideFence = outsideFence;
        this.forbiddenZone = forbiddenZone;
        this.battery = battery;
        this.signal = signal;
        this.worn = worn;
        this.result = result;
    }

    public Long getId() { return id; }
    public CorrectionObject getOffender() { return offender; }
    public String getClientPointId() { return clientPointId; }
    public Instant getPointTime() { return pointTime; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public Boolean getOfflineCaptured() { return offlineCaptured; }
    public Instant getReceivedAt() { return receivedAt; }
    public Boolean getOutsideFence() { return outsideFence; }
    public Boolean getForbiddenZone() { return forbiddenZone; }
    public Boolean getLeaveAuthorized() { return leaveAuthorized; }
    public Integer getBattery() { return battery; }
    public Integer getSignal() { return signal; }
    public Boolean getWorn() { return worn; }
    public IngestResult getResult() { return result; }

    public void setForbiddenZone(Boolean forbiddenZone) { this.forbiddenZone = forbiddenZone; }
    public void setLeaveAuthorized(Boolean leaveAuthorized) { this.leaveAuthorized = leaveAuthorized; }
    public void setBattery(Integer battery) { this.battery = battery; }
    public void setSignal(Integer signal) { this.signal = signal; }
    public void setWorn(Boolean worn) { this.worn = worn; }
}
