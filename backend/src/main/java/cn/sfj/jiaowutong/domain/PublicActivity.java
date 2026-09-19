package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 公益活动（社区服务）。由司法所（或区局指定司法所）发布，限定本所在矫对象在手机端报名。
 * 现场打卡以活动点中心 {@code (lat,lng)} 加半径 {@code radiusMeters} 构成圆形核验范围：
 * 打卡定位落在范围内判 NORMAL，落在范围外仍如实记录但标 ABNORMAL（异常打卡），不静默丢弃。
 * 时间窗 [startTime,endTime]（UTC）为活动现场打卡时段，逾期不能补打卡。
 */
@Entity
@Table(name = "public_activity", indexes = {
        @Index(name = "idx_activity_office", columnList = "office_id,status,start_time")
})
public class PublicActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "office_id")
    private JudicialOffice office;

    @Column(nullable = false, length = 128)
    private String title;

    /** 活动内容/注意事项 */
    @Column(nullable = false, length = 1024)
    private String detail;

    /** 活动地点名称 */
    @Column(nullable = false, length = 256)
    private String address;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    /** 现场打卡核验半径（米） */
    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters;

    /** 活动开始时刻（UTC，同时是打卡窗口起点） */
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    /** 活动结束时刻（UTC，打卡窗口终点） */
    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    /** 名额上限；null 表示不限 */
    private Integer capacity;

    /** PUBLISHED 已发布 / FINISHED 已结束（系统/人工归档）/ CANCELLED 已取消 */
    @Column(nullable = false, length = 16)
    private String status = "PUBLISHED";

    @Column(name = "created_by_id", nullable = false)
    private Long createdById;

    @Column(name = "created_by_name", nullable = false, length = 64)
    private String createdByName;

    @Column(nullable = false)
    private Instant createdAt;

    public PublicActivity() {
    }

    public PublicActivity(JudicialOffice office, String title, String detail, String address,
                          Double lat, Double lng, Integer radiusMeters,
                          Instant startTime, Instant endTime, Integer capacity,
                          Long createdById, String createdByName, Instant createdAt) {
        this.office = office;
        this.title = title;
        this.detail = detail;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.radiusMeters = radiusMeters;
        this.startTime = startTime;
        this.endTime = endTime;
        this.capacity = capacity;
        this.createdById = createdById;
        this.createdByName = createdByName;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public JudicialOffice getOffice() { return office; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public String getAddress() { return address; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public Integer getRadiusMeters() { return radiusMeters; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public Integer getCapacity() { return capacity; }
    public String getStatus() { return status; }
    public Long getCreatedById() { return createdById; }
    public String getCreatedByName() { return createdByName; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTitle(String title) { this.title = title; }
    public void setDetail(String detail) { this.detail = detail; }
    public void setAddress(String address) { this.address = address; }
    public void setLat(Double lat) { this.lat = lat; }
    public void setLng(Double lng) { this.lng = lng; }
    public void setRadiusMeters(Integer radiusMeters) { this.radiusMeters = radiusMeters; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public void setStatus(String status) { this.status = status; }
}
