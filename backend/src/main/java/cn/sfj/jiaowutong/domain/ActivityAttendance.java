package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 公益活动现场打卡。对象报名后到活动现场用手机打卡，服务端用当前定位与活动点
 * 中心+半径做范围核验（不信任客户端标记）：
 * <ul>
 *   <li>{@code punchTime}（UTC）落在活动 [start,end] 打卡窗内、且定位距活动点 ≤ radiusMeters
 *       → NORMAL 正常；</li>
 *   <li>定位落在核验范围外 → 仍如实记录为 ABNORMAL 异常打卡（不静默丢弃、不拒绝），
 *       详情写实测距离，并生成「公益活动异常打卡」违规红点供司法所核查；</li>
 *   <li>打卡窗之外、未报名、重复打卡等由服务层拒绝（异常情形不落正常记录）。</li>
 * </ul>
 */
@Entity
@Table(name = "activity_attendance", indexes = {
        @Index(name = "idx_attendance_activity", columnList = "activity_id"),
        @Index(name = "idx_attendance_offender", columnList = "offender_id")
})
public class ActivityAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id")
    private PublicActivity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offender_id")
    private CorrectionObject offender;

    /** NORMAL 范围内正常打卡 / ABNORMAL 范围外异常打卡 */
    @Column(nullable = false, length = 16)
    private String result;

    /** 打卡定位（GPS 采集时刻，UTC），受 5 分钟实时时效约束 */
    @Column(name = "punch_time", nullable = false)
    private Instant punchTime;

    @Column(nullable = false)
    private Instant recordedAt;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    /** 实测打卡点到活动点中心距离（米），服务端计算并留痕 */
    @Column(name = "distance_meters", nullable = false)
    private Double distanceMeters;

    /** 活动核验半径（米），打卡当时快照（活动半径后续被修改也不改变历史结论） */
    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters;

    public ActivityAttendance() {
    }

    public ActivityAttendance(PublicActivity activity, CorrectionObject offender, String result,
                              Instant punchTime, Instant recordedAt, Double lat, Double lng,
                              Double distanceMeters, Integer radiusMeters) {
        this.activity = activity;
        this.offender = offender;
        this.result = result;
        this.punchTime = punchTime;
        this.recordedAt = recordedAt;
        this.lat = lat;
        this.lng = lng;
        this.distanceMeters = distanceMeters;
        this.radiusMeters = radiusMeters;
    }

    public Long getId() { return id; }
    public PublicActivity getActivity() { return activity; }
    public CorrectionObject getOffender() { return offender; }
    public String getResult() { return result; }
    public Instant getPunchTime() { return punchTime; }
    public Instant getRecordedAt() { return recordedAt; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public Double getDistanceMeters() { return distanceMeters; }
    public Integer getRadiusMeters() { return radiusMeters; }
}
