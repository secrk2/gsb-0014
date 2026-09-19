package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.LocalTime;

/**
 * 禁区禁行时段（挂在 FORBIDDEN 围栏下；一个禁区可挂多个时段）。
 * 全部墙钟时间均以<b>对象所属司法所时区</b>解释，服务端把 UTC 时刻转到该时区后比较，
 * 不使用服务器/干警时区；DST 切换由 IANA tzdb 规则处理，切换日不会误报或漏报。
 * daysOfWeek 为逗号分隔的 MONDAY..SUNDAY，表示该时段“起始日”适用的星期。
 * endTime <= startTime 视为跨午夜时段（如 22:00-05:00）：
 * 判定“是否同一天禁行”时，凌晨的点要回溯前一天是否存在起始时段，避免把跨夜误拆成两天。
 */
@Entity
@Table(name = "fence_schedule")
public class FenceSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fence_id")
    private GeoFence fence;

    /** 适用起始星期，逗号分隔，如 "FRIDAY,SATURDAY"；为空表示每天 */
    @Column(length = 64)
    private String daysOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 64)
    private String label;

    public FenceSchedule() {
    }

    public FenceSchedule(GeoFence fence, String daysOfWeek, LocalTime startTime, LocalTime endTime, String label) {
        this.fence = fence;
        this.daysOfWeek = daysOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.label = label;
    }

    public Long getId() { return id; }
    public GeoFence getFence() { return fence; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getLabel() { return label; }

    public boolean crossesMidnight() {
        return !endTime.isAfter(startTime);
    }
}
