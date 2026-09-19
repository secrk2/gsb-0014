package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

/**
 * 司法所（矫正对象数据隔离的最小单元）。
 * timezone 为该所管辖对象的法定时区（IANA，如 Asia/Shanghai、Asia/Urumqi）：
 * 所有“今天/星期几/禁行时段”的判定都必须把 UTC 时刻转到该时区后再下结论，
 * 不能用服务器或干警所在时区代替，跨时区对象会因此误判越界/误报。
 */
@Entity
@Table(name = "judicial_office")
public class JudicialOffice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 机构编码，唯一 */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    /** 所属乡镇/街道 */
    @Column(length = 64)
    private String region;

    /**
     * 管辖对象所在时区（IANA 时区名）。
     * 时间一律按 UTC 存储，展示与“星期/时段/同一天”判定按本时区换算；
     * 用地区名而非固定偏移，DST 切换由 tzdb 规则处理，切换日不会误报。
     */
    @Column(nullable = false, length = 48)
    private String timezone = "Asia/Shanghai";

    /** 电子围栏中心（司法所/规定活动区域）；未配置多边形活动范围时的兜底圆形围栏 */
    @Column(nullable = false)
    private Double centerLat;

    @Column(nullable = false)
    private Double centerLng;

    /** 圆形围栏半径（米），与 centerLat/Lng 构成兜底活动范围 */
    @Column(nullable = false)
    private Integer fenceRadiusMeters;

    public JudicialOffice() {
    }

    public JudicialOffice(String code, String name, String region,
                          Double centerLat, Double centerLng, Integer fenceRadiusMeters) {
        this(code, name, region, "Asia/Shanghai", centerLat, centerLng, fenceRadiusMeters);
    }

    public JudicialOffice(String code, String name, String region, String timezone,
                          Double centerLat, Double centerLng, Integer fenceRadiusMeters) {
        this.code = code;
        this.name = name;
        this.region = region;
        this.timezone = timezone;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.fenceRadiusMeters = fenceRadiusMeters;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getRegion() { return region; }
    public String getTimezone() { return timezone; }
    public Double getCenterLat() { return centerLat; }
    public Double getCenterLng() { return centerLng; }
    public Integer getFenceRadiusMeters() { return fenceRadiusMeters; }

    public void setTimezone(String timezone) { this.timezone = timezone; }
}
