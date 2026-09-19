package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.*;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.dto.ClearTracksRequest;
import cn.sfj.jiaowutong.web.dto.VerifyPointRequest;
import cn.sfj.jiaowutong.web.vo.CompletionView;
import cn.sfj.jiaowutong.web.vo.FenceView;
import cn.sfj.jiaowutong.web.vo.MonitorOverviewView;
import cn.sfj.jiaowutong.web.vo.TrackReplayView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 定位监控：
 * - 总览：在矫对象实时位置/设备状态/心跳在线判定（UTC 年龄，与时区无关）；
 * - 周(7d)/月(30d)轨迹：时间窗按“对象司法所时区的当前时刻”向前取，再转 UTC 查询；
 *   since 增量供前端每 5 秒轮询，不重复传全量；
 * - 越界落点二次核实：服务端用围栏几何重算，结论+原因留痕，不信任客户端标记；
 * - 清除轨迹：高危操作，删除并留痕，使前端能区分“从无轨迹”与“轨迹已清空”；
 * - 在矫完成度：打卡天数 / 关键报到 双口径并列，口径定义写进返回体供界面上墙。
 */
@Service
public class MonitorService {

    /** 超过此时长无有效点视为信号延迟 */
    private static final long STALE_AFTER_SEC = 30;
    /** 超过此时长无有效点视为离线 */
    private static final long OFFLINE_AFTER_SEC = 15 * 60;
    /** 首屏抽稀后的最大点数（5s 一帧 30 天理论 50 万点，不抽稀无法渲染） */
    private static final int FIRST_LOAD_MAX_POINTS = 1500;
    /** 两口径相差超过该百分点视为“结论相反”，界面需显著提示 */
    private static final double OPPOSITE_GAP = 0.25d;

    private final CorrectionObjectRepository objectRepository;
    private final TrackPointRepository trackPointRepository;
    private final CheckInRepository checkInRepository;
    private final GeoFenceRepository geoFenceRepository;
    private final FenceScheduleRepository scheduleRepository;
    private final MonitorActionRepository actionRepository;
    private final AccessControlService accessControl;
    private final FenceService fenceService;
    private final ObjectMapper objectMapper;

    public MonitorService(CorrectionObjectRepository objectRepository,
                          TrackPointRepository trackPointRepository,
                          CheckInRepository checkInRepository,
                          GeoFenceRepository geoFenceRepository,
                          FenceScheduleRepository scheduleRepository,
                          MonitorActionRepository actionRepository,
                          AccessControlService accessControl,
                          FenceService fenceService,
                          ObjectMapper objectMapper) {
        this.objectRepository = objectRepository;
        this.trackPointRepository = trackPointRepository;
        this.checkInRepository = checkInRepository;
        this.geoFenceRepository = geoFenceRepository;
        this.scheduleRepository = scheduleRepository;
        this.actionRepository = actionRepository;
        this.accessControl = accessControl;
        this.fenceService = fenceService;
        this.objectMapper = objectMapper;
    }

    // ---------------- 总览 ----------------

    @Transactional(readOnly = true)
    public MonitorOverviewView overview(LoginUser user) {
        Instant now = Instant.now();
        Set<CorrectionStatus> active = EnumSet.of(
                CorrectionStatus.SERVING, CorrectionStatus.LEAVE, CorrectionStatus.ADMONISHED);
        List<CorrectionObject> scoped = accessControl.filterByScope(objectRepository.findAll(), user).stream()
                .filter(o -> active.contains(o.getStatus()))
                .sorted(Comparator.comparing(CorrectionObject::getCorrectionNo))
                .toList();

        List<MonitorOverviewView.Item> items = new ArrayList<>();
        for (CorrectionObject o : scoped) {
            Instant last = o.getLastLocationAt();
            Long ageSec = last == null ? null : Duration.between(last, now).getSeconds();
            String link;
            if (last == null) {
                link = "NEVER";
            } else if (ageSec <= STALE_AFTER_SEC) {
                link = "ONLINE";
            } else if (ageSec <= OFFLINE_AFTER_SEC) {
                link = "STALE";
            } else {
                link = "OFFLINE";
            }
            long drift24h = trackPointRepository.countByOffender_IdAndResultAndPointTimeAfter(
                    o.getId(), TrackPoint.IngestResult.DRIFT_DISCARDED, now.minusSeconds(86400));
            items.add(new MonitorOverviewView.Item(
                    o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                    o.getOffice().getId(), o.getOffice().getName(), o.getOffice().getTimezone(),
                    o.getStatus().name(), o.getStatus().getLabel(),
                    last, ageSec, link,
                    o.getLastLat(), o.getLastLng(),
                    !Boolean.FALSE.equals(o.getLastInsideFence()),
                    Boolean.TRUE.equals(o.getLastForbidden()),
                    Boolean.TRUE.equals(o.getLastLeaveAuthorized()),
                    o.getLastBattery(), o.getLastSignal(), o.getLastWorn(), drift24h));
        }
        return new MonitorOverviewView(now, MonitorOverviewView.ONLINE_WITHIN_SEC, items);
    }

    // ---------------- 周/月轨迹 ----------------

    @Transactional(readOnly = true)
    public TrackReplayView replay(Long id, String range, Instant since, LoginUser user) {
        CorrectionObject o = accessControl.loadVisible(id, user);
        ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
        boolean week = !"MONTH".equalsIgnoreCase(range);
        int days = week ? 7 : 30;

        // 时间窗按对象时区的“现在”回取，再转 UTC；跨所干警查看时绝不套用自己的时区
        ZonedDateTime nowLocal = Instant.now().atZone(zone);
        ZonedDateTime fromLocal = nowLocal.toLocalDate().atStartOfDay(zone).minusDays(days - 1L);
        Instant windowFrom = fromLocal.toInstant();
        Instant windowTo = nowLocal.toInstant().plusSeconds(1);

        List<TrackPoint> accepted = trackPointRepository
                .findByOffender_IdAndResultAndPointTimeBetweenOrderByPointTimeAscIdAsc(
                        id, TrackPoint.IngestResult.ACCEPTED, windowFrom, windowTo);
        long driftInWindow = trackPointRepository
                .countByOffender_IdAndResultAndPointTimeBetween(
                        id, TrackPoint.IngestResult.DRIFT_DISCARDED, windowFrom, windowTo);

        // 5s 增量轮询：只回 since 之后的新点，不抽稀
        boolean incremental = since != null;
        List<TrackPoint> shown;
        if (incremental) {
            Instant sinceBound = since;
            shown = accepted.stream().filter(p -> p.getPointTime().isAfter(sinceBound)).toList();
        } else {
            shown = thinOut(accepted);
        }

        Map<Long, String> verified = loadVerifyMap(id,
                shown.stream().map(TrackPoint::getId).toList());

        List<TrackReplayView.PointView> points = shown.stream()
                .map(p -> new TrackReplayView.PointView(
                        p.getId(), p.getPointTime(), p.getLat(), p.getLng(),
                        Boolean.TRUE.equals(p.getOfflineCaptured()),
                        Boolean.TRUE.equals(p.getOutsideFence()),
                        Boolean.TRUE.equals(p.getForbiddenZone()),
                        Boolean.TRUE.equals(p.getLeaveAuthorized()),
                        p.getBattery(), p.getSignal(), p.getWorn(),
                        verified.containsKey(p.getId()), verified.get(p.getId())))
                .toList();

        TrackReplayView.ClearRecord lastClear = actionRepository
                .findFirstByOffenderIdAndActionOrderByCreatedAtDescIdDesc(id, "CLEAR_TRACKS")
                .map(a -> new TrackReplayView.ClearRecord(
                        a.getCreatedAt(), a.getOperatorName(), a.getReason(),
                        a.getClearedCount() == null ? 0 : a.getClearedCount()))
                .orElse(null);

        List<FenceView> fences = buildFenceViews(o.getOffice().getId());

        return new TrackReplayView(
                o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                o.getOffice().getTimezone(), week ? "WEEK" : "MONTH",
                windowFrom, windowTo, Instant.now(), incremental,
                points, accepted.size(), driftInWindow, fences, lastClear,
                buildCompletion(o, zone));
    }

    /**
     * 首屏抽稀：等间隔取点保证轮廓，但越界/禁区/离线补传点全部保留（它们是核查重点）。
     */
    private List<TrackPoint> thinOut(List<TrackPoint> all) {
        if (all.size() <= FIRST_LOAD_MAX_POINTS) {
            return all;
        }
        List<TrackPoint> hot = all.stream()
                .filter(p -> Boolean.TRUE.equals(p.getOutsideFence())
                        || Boolean.TRUE.equals(p.getForbiddenZone())
                        || Boolean.TRUE.equals(p.getOfflineCaptured()))
                .toList();
        int normalBudget = FIRST_LOAD_MAX_POINTS - Math.min(hot.size(), FIRST_LOAD_MAX_POINTS / 2);
        List<TrackPoint> normal = new ArrayList<>();
        long normalCount = all.size() - hot.size();
        if (normalCount <= normalBudget) {
            normal = all.stream().filter(p -> !(Boolean.TRUE.equals(p.getOutsideFence())
                    || Boolean.TRUE.equals(p.getForbiddenZone())
                    || Boolean.TRUE.equals(p.getOfflineCaptured()))).toList();
        } else {
            java.util.Set<TrackPoint> hotSet = Set.copyOf(hot);
            double step = (double) normalCount / normalBudget;
            int idx = 0;
            for (TrackPoint p : all) {
                if (hotSet.contains(p)) continue;
                if (Math.floor(idx / step) != Math.floor((idx - 1) / step) || idx == 0) {
                    normal.add(p);
                }
                idx++;
            }
        }
        List<TrackPoint> merged = new ArrayList<>(hot.size() + normal.size());
        merged.addAll(hot);
        merged.addAll(normal);
        merged.sort(Comparator.comparing(TrackPoint::getPointTime).thenComparing(TrackPoint::getId));
        return merged;
    }

    private Map<Long, String> loadVerifyMap(Long offenderId, List<Long> pointIds) {
        if (pointIds.isEmpty()) {
            return Map.of();
        }
        return actionRepository
                .findByOffenderIdAndActionAndPointIdIn(offenderId, "VERIFY_POINT", pointIds).stream()
                .collect(Collectors.toMap(MonitorAction::getPointId, MonitorAction::getConclusion, (a, b) -> a));
    }

    private List<FenceView> buildFenceViews(Long officeId) {
        List<GeoFence> fences = geoFenceRepository.findByOffice_IdAndEnabledTrue(officeId);
        List<FenceSchedule> schedules = scheduleRepository.findByFence_Office_Id(officeId);
        List<FenceView> result = new ArrayList<>();
        for (GeoFence f : fences) {
            List<FenceView.ScheduleView> sc = schedules.stream()
                    .filter(s -> s.getFence().getId().equals(f.getId()))
                    .map(s -> new FenceView.ScheduleView(
                            s.getDaysOfWeek() == null ? "" : s.getDaysOfWeek(),
                            s.getStartTime().toString(), s.getEndTime().toString(),
                            s.getLabel(), s.crossesMidnight()))
                    .toList();
            List<List<Double>> polygon = parsePolygon(f.getPolygonJson());
            result.add(new FenceView(f.getId(), f.getName(), f.getKind().name(), polygon,
                    f.getCenterLat(), f.getCenterLng(), f.getRadiusMeters(), sc));
        }
        return result;
    }

    private List<List<Double>> parsePolygon(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            double[][] pts = objectMapper.readValue(json, double[][].class);
            List<List<Double>> out = new ArrayList<>();
            for (double[] p : pts) {
                out.add(List.of(p[0], p[1]));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- 越界落点二次核实（留痕，服务端重算） ----------------

    @Transactional
    public Map<String, Object> verifyPoint(Long objectId, Long pointId,
                                           VerifyPointRequest req, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        CorrectionObject o = accessControl.loadVisible(objectId, user);
        TrackPoint point = trackPointRepository.findById(pointId)
                .orElseThrow(() -> ApiException.notFound("轨迹点不存在，可能已随轨迹清除"));
        if (!point.getOffender().getId().equals(objectId)) {
            throw ApiException.notFound("该轨迹点不属于此对象");
        }

        // 服务端重算：越界与禁行均按当前围栏配置 + 对象时区重放该点，不轻信前端标记
        FenceService.OfficeFences fences = fenceService.load(o.getOffice());
        boolean serverOutside = !fences.insideRange(point.getLat(), point.getLng());
        FenceService.ForbiddenHit hit = fences.forbiddenAt(point.getLat(), point.getLng(), point.getPointTime());
        boolean serverAnomaly = serverOutside || hit != null;

        String conclusion = req.conclusion();
        if (conclusion.equals("REALLY_BREACH") && !serverAnomaly) {
            throw ApiException.badRequest("POINT_NOT_ANOMALY",
                    "服务端重算该点当前并不在活动范围外、也未命中禁行时段，不能标记为确认越界，请核对后再操作");
        }

        boolean already = actionRepository
                .existsByOffenderIdAndActionAndPointId(objectId, "VERIFY_POINT", pointId);
        actionRepository.save(new MonitorAction(objectId, "VERIFY_POINT",
                user.userId(), user.realName(), req.reason(), conclusion, pointId, null,
                "服务端重算 outside=" + serverOutside
                        + (hit != null ? " forbidden=" + hit.scheduleLabel() : "")
                        + " localTime=" + TrackService.fmtLocal(point.getPointTime(), fences.zone())));

        return Map.of(
                "verified", true,
                "alreadyVerified", already,
                "conclusion", conclusion,
                "serverRecomputed", true,
                "serverOutsideFence", serverOutside,
                "serverForbidden", hit != null,
                "pointTime", point.getPointTime());
    }

    // ---------------- 清除轨迹（留痕） ----------------

    @Transactional
    public Map<String, Object> clearTracks(Long objectId, ClearTracksRequest req, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        CorrectionObject o = accessControl.loadVisible(objectId, user);
        long accepted = trackPointRepository.countByOffender_IdAndResult(
                objectId, TrackPoint.IngestResult.ACCEPTED);
        long drift = trackPointRepository.countByOffender_IdAndResult(
                objectId, TrackPoint.IngestResult.DRIFT_DISCARDED);

        long deleted = trackPointRepository.deleteByOffender_Id(objectId);
        trackPointRepository.flush();

        // 清空对象实时定位状态，避免总览仍显示旧坐标
        o.setLastLocationAt(null);
        o.setLastLat(null);
        o.setLastLng(null);
        o.setLastInsideFence(null);
        o.setLastForbidden(null);
        objectRepository.save(o);

        actionRepository.save(new MonitorAction(objectId, "CLEAR_TRACKS",
                user.userId(), user.realName(), req.reason(), null, null, (int) accepted,
                "删除有效点 " + accepted + "、漂移点 " + drift + "，共 " + deleted + " 条"));

        return Map.of("deleted", deleted, "accepted", accepted, "drift", drift, "cleared", true);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> actionLog(Long objectId, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        accessControl.loadVisible(objectId, user);
        return actionRepository.findByOffenderIdOrderByCreatedAtDescIdDesc(objectId).stream()
                .limit(30)
                .map(a -> Map.<String, Object>of(
                        "action", a.getAction(),
                        "operatorName", a.getOperatorName(),
                        "reason", a.getReason(),
                        "conclusion", a.getConclusion() == null ? "" : a.getConclusion(),
                        "detail", a.getDetail() == null ? "" : a.getDetail(),
                        "createdAt", a.getCreatedAt()))
                .toList();
    }

    // ---------------- 在矫完成度（双口径） ----------------

    private CompletionView buildCompletion(CorrectionObject o, ZoneId zone) {
        // 与“月视图”一致取近 30 天（按对象时区的日历日）为统一窗口，两口径才可比；
        // 不用整个矫正期，否则打卡天数口径分母是自然日，数值恒低、失去区分度。
        LocalDate today = Instant.now().atZone(zone).toLocalDate();
        LocalDate basisFrom = today.minusDays(29);
        LocalDate basisTo = today;

        Set<LocalDate> checkDays = checkInRepository.findByOffender_IdOrderByCheckDateAscIdAsc(o.getId())
                .stream()
                .map(CheckIn::getCheckDate)
                .filter(d -> !d.isBefore(basisFrom) && !d.isAfter(basisTo))
                .collect(Collectors.toCollection(java.util.TreeSet::new));

        // 口径一：打卡天数 / 自然日
        int calendarDays = (int) (java.time.temporal.ChronoUnit.DAYS.between(basisFrom, basisTo) + 1);
        int actualDays = checkDays.size();
        double dayRate = round1((double) actualDays / calendarDays);

        // 口径二：规定报到日（星期匹配）当天完成的节点数 / 应到节点数
        DayOfWeek reportDay = parseDay(o.getReportDay());
        int dueNodes = 0;
        int doneNodes = 0;
        for (LocalDate d = basisFrom; !d.isAfter(basisTo); d = d.plusDays(1)) {
            if (reportDay != null && d.getDayOfWeek() == reportDay) {
                dueNodes++;
                if (checkDays.contains(d)) {
                    doneNodes++;
                }
            }
        }
        double keyRate = dueNodes == 0 ? 0d : round1((double) doneNodes / dueNodes);
        boolean opposite = Math.abs(dayRate - keyRate) >= OPPOSITE_GAP;

        return new CompletionView(
                basisFrom.toString(), basisTo.toString(), zone.toString(),
                actualDays, calendarDays, dayRate,
                doneNodes, dueNodes, keyRate, opposite,
                CompletionView.CHECKIN_DAY_DEFINITION, CompletionView.KEY_REPORT_DEFINITION);
    }

    private DayOfWeek parseDay(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return DayOfWeek.valueOf(name.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private double round1(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
