package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

/**
 * 电子围栏（司法所级配置，对本所全部对象生效）。
 * 两类：
 * - ALLOW_RANGE 活动范围：多边形（polygonJson）为空时回退到所中心圆；点必须落在范围内；
 * - FORBIDDEN 禁区：多边形，任意时刻或按 {@link FenceSchedule} 排程禁行。
 * polygonJson 形如 [[lat,lng],[lat,lng],...]，顶点顺序不限，首尾不必重复。
 */
@Entity
@Table(name = "geo_fence", indexes = {
        @Index(name = "idx_fence_office", columnList = "office_id,enabled")
})
public class GeoFence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "office_id")
    private JudicialOffice office;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FenceKind kind;

    /** 多边形顶点 JSON：[[lat,lng],...]；ALLOW_RANGE 可为空（回退圆形） */
    @Column(name = "polygon_json", length = 4000)
    private String polygonJson;

    /** 圆形围栏中心/半径（多边形为空时使用） */
    private Double centerLat;
    private Double centerLng;
    private Integer radiusMeters;

    @Column(nullable = false)
    private Boolean enabled = true;

    public enum FenceKind { ALLOW_RANGE, FORBIDDEN }

    public GeoFence() {
    }

    public GeoFence(JudicialOffice office, String name, FenceKind kind, String polygonJson,
                    Double centerLat, Double centerLng, Integer radiusMeters, Boolean enabled) {
        this.office = office;
        this.name = name;
        this.kind = kind;
        this.polygonJson = polygonJson;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.radiusMeters = radiusMeters;
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public JudicialOffice getOffice() { return office; }
    public String getName() { return name; }
    public FenceKind getKind() { return kind; }
    public String getPolygonJson() { return polygonJson; }
    public Double getCenterLat() { return centerLat; }
    public Double getCenterLng() { return centerLng; }
    public Integer getRadiusMeters() { return radiusMeters; }
    public Boolean getEnabled() { return enabled; }

    public void setName(String name) { this.name = name; }
    public void setPolygonJson(String polygonJson) { this.polygonJson = polygonJson; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
