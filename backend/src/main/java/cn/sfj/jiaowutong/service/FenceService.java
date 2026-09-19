package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.domain.FenceSchedule;
import cn.sfj.jiaowutong.domain.GeoFence;
import cn.sfj.jiaowutong.domain.JudicialOffice;
import cn.sfj.jiaowutong.repo.FenceScheduleRepository;
import cn.sfj.jiaowutong.repo.GeoFenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 电子围栏判定服务：
 * 1. 活动范围：ALLOW_RANGE 多边形（未配置时回退司法所中心圆），点在范围内才算正常；
 * 2. 禁区：FORBIDDEN 多边形命中 + 禁行时段生效才算“禁区闯入”；
 * 3. 禁行时段全部以对象所属司法所时区解释（{@link ZoneId}），DST 切换由 tzdb 处理；
 *    跨午夜时段（如周五 22:00–次日 05:00）在“同一天禁行”判定时回溯前一日星期。
 */
@Service
public class FenceService {

    private final GeoFenceRepository fenceRepository;
    private final FenceScheduleRepository scheduleRepository;
    private final ObjectMapper objectMapper;

    public FenceService(GeoFenceRepository fenceRepository,
                        FenceScheduleRepository scheduleRepository,
                        ObjectMapper objectMapper) {
        this.fenceRepository = fenceRepository;
        this.scheduleRepository = scheduleRepository;
        this.objectMapper = objectMapper;
    }

    /** 解析后的多边形（平行纬/经数组，供 ray-casting） */
    public record Polygon(double[] lats, double[] lngs) {
        public boolean contains(double lat, double lng) {
            return GeoUtil.isInsidePolygon(lat, lng, lats, lngs);
        }
    }

    public record ForbiddenHit(Long fenceId, String fenceName, String scheduleLabel) {
    }

    /** 某司法所一次判定所需的全部围栏上下文（一次查询内复用，避免逐点查库） */
    public final class OfficeFences {
        final JudicialOffice office;
        final ZoneId zone;
        final List<RuntimeFence> allow;
        final List<RuntimeFence> forbidden;

        OfficeFences(JudicialOffice office, List<RuntimeFence> allow, List<RuntimeFence> forbidden) {
            this.office = office;
            this.zone = safeZone(office.getTimezone());
            this.allow = allow;
            this.forbidden = forbidden;
        }

        public JudicialOffice office() { return office; }
        public ZoneId zone() { return zone; }
        public List<RuntimeFence> allowFences() { return allow; }
        public List<RuntimeFence> forbiddenFences() { return forbidden; }

        /** 点是否在活动范围内 */
        public boolean insideRange(double lat, double lng) {
            if (allow.isEmpty()) {
                // 未配置多边形活动范围：回退司法所中心圆
                return GeoUtil.isInsideCircle(lat, lng,
                        office.getCenterLat(), office.getCenterLng(), office.getFenceRadiusMeters());
            }
            for (RuntimeFence f : allow) {
                if (f.contains(lat, lng)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 禁区判定：先几何命中，再判该 UTC 时刻在对象时区是否处于禁行时段。
         * 无排程的禁区视为全天禁行。
         */
        public ForbiddenHit forbiddenAt(double lat, double lng, Instant at) {
            ZonedDateTime local = at.atZone(zone);
            for (RuntimeFence f : forbidden) {
                if (!f.contains(lat, lng)) {
                    continue;
                }
                if (f.schedules.isEmpty()) {
                    return new ForbiddenHit(f.fence.getId(), f.fence.getName(), "全天禁行");
                }
                for (FenceSchedule sc : f.schedules) {
                    String label = scheduleActive(sc, local);
                    if (label != null) {
                        return new ForbiddenHit(f.fence.getId(), f.fence.getName(), label);
                    }
                }
            }
            return null;
        }
    }

    static final class RuntimeFence {
        final GeoFence fence;
        final Polygon polygon;
        final List<FenceSchedule> schedules;

        RuntimeFence(GeoFence fence, Polygon polygon, List<FenceSchedule> schedules) {
            this.fence = fence;
            this.polygon = polygon;
            this.schedules = schedules;
        }

        boolean contains(double lat, double lng) {
            if (polygon != null) {
                return polygon.contains(lat, lng);
            }
            return GeoUtil.isInsideCircle(lat, lng,
                    fence.getCenterLat(), fence.getCenterLng(), fence.getRadiusMeters());
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public OfficeFences load(JudicialOffice office) {
        List<GeoFence> fences = fenceRepository.findByOffice_IdAndEnabledTrue(office.getId());
        List<FenceSchedule> allSchedules = scheduleRepository.findByFence_Office_Id(office.getId());

        List<RuntimeFence> allow = new ArrayList<>();
        List<RuntimeFence> forbidden = new ArrayList<>();
        for (GeoFence f : fences) {
            Polygon poly = parsePolygon(f.getPolygonJson());
            List<FenceSchedule> sc = allSchedules.stream()
                    .filter(s -> s.getFence().getId().equals(f.getId()))
                    .toList();
            RuntimeFence rf = new RuntimeFence(f, poly, sc);
            if (f.getKind() == GeoFence.FenceKind.ALLOW_RANGE) {
                allow.add(rf);
            } else {
                forbidden.add(rf);
            }
        }
        return new OfficeFences(office, allow, forbidden);
    }

    /**
     * 单个禁行时段在给定本地时刻是否生效。
     * 非跨午夜：当天星期匹配且墙钟落在 [start, end)；
     * 跨午夜：当天星期匹配且 >= start（前半夜），或“起始日”为昨天星期且 < end（后半夜）。
     * 返回生效时段说明；不生效返回 null。
     */
    static String scheduleActive(FenceSchedule sc, ZonedDateTime local) {
        LocalTime t = local.toLocalTime();
        Set<DayOfWeek> days = parseDays(sc.getDaysOfWeek());
        boolean todayMatch = days.isEmpty() || days.contains(local.getDayOfWeek());

        if (!sc.crossesMidnight()) {
            if (todayMatch && !t.isBefore(sc.getStartTime()) && t.isBefore(sc.getEndTime())) {
                return sc.getLabel();
            }
            return null;
        }
        // 跨午夜：后半夜属于“前一天起始”的同一次禁行
        boolean yesterdayMatch = days.isEmpty()
                || days.contains(local.getDayOfWeek().minus(1));
        if (todayMatch && !t.isBefore(sc.getStartTime())) {
            return sc.getLabel();
        }
        if (yesterdayMatch && t.isBefore(sc.getEndTime())) {
            return sc.getLabel();
        }
        return null;
    }

    private static Set<DayOfWeek> parseDays(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }

    static ZoneId safeZone(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (Exception e) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private Polygon parsePolygon(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            double[][] pts = objectMapper.readValue(json, double[][].class);
            if (pts == null || pts.length < 3) {
                return null;
            }
            double[] lats = new double[pts.length];
            double[] lngs = new double[pts.length];
            for (int i = 0; i < pts.length; i++) {
                if (pts[i] == null || pts[i].length < 2) {
                    return null;
                }
                lats[i] = pts[i][0];
                lngs[i] = pts[i][1];
            }
            return new Polygon(lats, lngs);
        } catch (Exception e) {
            return null;
        }
    }
}
