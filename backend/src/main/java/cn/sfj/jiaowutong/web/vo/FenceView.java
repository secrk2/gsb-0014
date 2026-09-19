package cn.sfj.jiaowutong.web.vo;

import java.util.List;

/**
 * 围栏下发给监控视图：多边形顶点 [[lat,lng],...]；圆形兜底给中心与半径。
 * 禁行时段的 days/start/end 均按司法所时区解释，crossesMidnight=true 为跨午夜时段。
 */
public record FenceView(Long id, String name, String kind,
                        List<List<Double>> polygon,
                        Double centerLat, Double centerLng, Integer radiusMeters,
                        List<ScheduleView> schedules) {

    public record ScheduleView(String daysOfWeek, String startTime, String endTime,
                               String label, boolean crossesMidnight) {
    }
}
