package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.CorrectionObjectRepository;
import cn.sfj.jiaowutong.repo.TrackPointRepository;
import cn.sfj.jiaowutong.repo.ViolationEventRepository;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.dto.TrackBatchRequest;
import cn.sfj.jiaowutong.web.vo.TrackIngestView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 腕表定位上报，服务端强约束（不依赖终端自觉）：
 * 1. 防旧位置糊弄：实时点采集时刻距服务端时钟不得超过 {@link #REALTIME_SKEW_MIN} 分钟，
 *    离线补传点不得早于 {@link #OFFLINE_MAX_AGE_HOURS} 小时，亦不得来自未来；时间一律 UTC；
 * 2. 幂等：同一对象同一 clientPointId 只入库一次，断网恢复重放不产生重复点；
 * 3. GPS 漂移过滤：按采集时间排序后逐点比对上一可信锚点，等效速度超 {@link #MAX_SPEED_MPS}
 *    且跳变距离超 {@link #JUMP_MIN_METERS} 判为漂移，标记 DRIFT_DISCARDED 留痕——
 *    不连线、不更新锚点（避免下一点被带偏）、不更新位置、不触发越界；
 * 4. 电子围栏：活动范围（多边形，缺省回退圆）+ 禁区多边形；禁行时段按司法所时区解释；
 * 5. 合并：对象最新位置取所有已接收 ACCEPTED 点中采集时间最大者，乱序/迟到批次不让位置回退；
 * 6. 红点边沿：仅在「围栏内→外」「进入禁区」边沿、且无同类未处置红点时报警一次，补传不刷屏。
 */
@Service
public class TrackService {

    /** 实时点允许的时钟偏移（分钟） */
    private static final long REALTIME_SKEW_MIN = 5;
    /** 离线补传点最大可追溯时长（小时） */
    private static final long OFFLINE_MAX_AGE_HOURS = 72;
    /** 容忍的设备时钟超前（分钟） */
    private static final long FUTURE_SKEW_MIN = 2;
    /** 合理移动速度上限（米/秒）≈162 km/h，高速公路上限，步行/公交/火车均不超过 */
    private static final double MAX_SPEED_MPS = 45.0d;
    /** 低于该距离的跳变不判漂移（吸收常规 GPS 抖动，单位米） */
    private static final double JUMP_MIN_METERS = 100.0d;

    private static final DateTimeFormatter LOCAL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TrackPointRepository trackPointRepository;
    private final CorrectionObjectRepository objectRepository;
    private final ViolationEventRepository violationRepository;
    private final FenceService fenceService;
    private final LeaveService leaveService;

    public TrackService(TrackPointRepository trackPointRepository,
                        CorrectionObjectRepository objectRepository,
                        ViolationEventRepository violationRepository,
                        FenceService fenceService,
                        LeaveService leaveService) {
        this.trackPointRepository = trackPointRepository;
        this.objectRepository = objectRepository;
        this.violationRepository = violationRepository;
        this.fenceService = fenceService;
        this.leaveService = leaveService;
    }

    @Transactional
    public TrackIngestView ingest(TrackBatchRequest request, LoginUser user) {
        if (user.role() != Role.OFFENDER || user.offenderId() == null) {
            throw ApiException.forbidden("仅矫正对象本人账号可上报定位轨迹");
        }
        CorrectionObject obj = objectRepository.findById(user.offenderId())
                .orElseThrow(() -> ApiException.notFound("本人档案不存在"));

        Instant now = Instant.now();
        Instant realtimeFloor = now.minusSeconds(REALTIME_SKEW_MIN * 60);
        Instant offlineFloor = now.minusSeconds(OFFLINE_MAX_AGE_HOURS * 3600);
        Instant futureCeil = now.plusSeconds(FUTURE_SKEW_MIN * 60);

        FenceService.OfficeFences fences = fenceService.load(obj.getOffice());
        // 请销假联动：一次取出该对象全部“准假外出”窗口，逐点在内存判，避免逐点查库。
        // 点几何上越界但采集时刻落在已批准假期 [start,end] 内 → 准假外出，不算越界红点。
        List<LeaveApplication> awayLeaves = leaveService.loadAwayLeaves(obj.getId());

        int duplicates = 0;
        int outsideCount = 0;
        int forbiddenCount = 0;
        int leaveAuthorizedCount = 0;
        List<TrackIngestView.RejectedPoint> rejected = new ArrayList<>();

        // 已存在的全部有效点：用于幂等、找漂移锚点、合并最新位置
        List<TrackPoint> existingAccepted = trackPointRepository
                .findByOffender_IdAndResultOrderByPointTimeAscIdAsc(obj.getId(), TrackPoint.IngestResult.ACCEPTED);
        Set<String> existingIds = new java.util.HashSet<>();
        // 已判漂移丢弃的点同样占用幂等键
        trackPointRepository.findByOffender_IdOrderByPointTimeAscIdAsc(obj.getId())
                .forEach(p -> existingIds.add(p.getClientPointId()));

        // 本批时效通过、待漂移质检的点（按采集时间排序，乱序批次也能正确连线）
        List<TrackBatchRequest.PointDto> candidates = new ArrayList<>();
        java.util.Set<String> seenInBatch = new java.util.HashSet<>();

        for (TrackBatchRequest.PointDto p : request.points()) {
            if (!seenInBatch.add(p.clientPointId()) || existingIds.contains(p.clientPointId())) {
                duplicates++;
                continue;
            }
            if (p.pointTime().isAfter(futureCeil)) {
                rejected.add(new TrackIngestView.RejectedPoint(p.clientPointId(),
                        "定位时间晚于当前时间，疑似伪造定位（" + p.pointTime() + "）"));
                continue;
            }
            if (Boolean.FALSE.equals(p.offlineCaptured()) && p.pointTime().isBefore(realtimeFloor)) {
                rejected.add(new TrackIngestView.RejectedPoint(p.clientPointId(),
                        "实时上报点采集于 " + p.pointTime() + "，已超过 " + REALTIME_SKEW_MIN
                                + " 分钟时效，禁止用缓存旧位置冒充当前位置"));
                continue;
            }
            if (Boolean.TRUE.equals(p.offlineCaptured()) && p.pointTime().isBefore(offlineFloor)) {
                rejected.add(new TrackIngestView.RejectedPoint(p.clientPointId(),
                        "离线补传点超出 " + OFFLINE_MAX_AGE_HOURS + " 小时可追溯窗口，不予采信"));
                continue;
            }
            candidates.add(p);
        }
        candidates.sort(Comparator.comparing(TrackBatchRequest.PointDto::pointTime));

        // 漂移锚点：本批最早候选点之前最近的一个有效点
        TrackPoint anchor = null;
        if (!candidates.isEmpty()) {
            Instant firstTime = candidates.get(0).pointTime();
            for (int i = existingAccepted.size() - 1; i >= 0; i--) {
                if (!existingAccepted.get(i).getPointTime().isAfter(firstTime)) {
                    anchor = existingAccepted.get(i);
                    break;
                }
            }
        }

        List<TrackPoint> toSave = new ArrayList<>();
        int accepted = 0;
        int driftDiscarded = 0;

        for (TrackBatchRequest.PointDto p : candidates) {
            boolean drift = false;
            if (anchor != null) {
                long dt = java.time.Duration.between(anchor.getPointTime(), p.pointTime()).getSeconds();
                double dist = GeoUtil.distanceMeters(anchor.getLat(), anchor.getLng(), p.lat(), p.lng());
                double speed = GeoUtil.speedMps(dist, dt);
                if (dt > 0 && dist >= JUMP_MIN_METERS && speed > MAX_SPEED_MPS) {
                    drift = true;
                }
            }

            boolean geometricOutside;
            boolean leaveAuthorized = false;
            FenceService.ForbiddenHit hit;
            if (drift) {
                // 漂移点不做围栏判定：几何结果不可信，避免误报越界/禁区
                geometricOutside = false;
                hit = null;
            } else {
                geometricOutside = !fences.insideRange(p.lat(), p.lng());
                hit = fences.forbiddenAt(p.lat(), p.lng(), p.pointTime());
                // 准假外出：几何越界但定位时刻落在已批准假期窗口内（禁区不因此解禁）
                leaveAuthorized = geometricOutside
                        && LeaveService.withinAnyLeave(awayLeaves, p.pointTime());
            }
            final boolean outside = geometricOutside && !leaveAuthorized;

            TrackPoint point = new TrackPoint(obj, p.clientPointId(), p.pointTime(),
                    p.lat(), p.lng(), p.offlineCaptured(), now,
                    outside, hit != null, p.battery(), p.signal(), p.worn(),
                    drift ? TrackPoint.IngestResult.DRIFT_DISCARDED : TrackPoint.IngestResult.ACCEPTED);
            point.setLeaveAuthorized(leaveAuthorized);
            toSave.add(point);

            if (drift) {
                driftDiscarded++;
                rejected.add(new TrackIngestView.RejectedPoint(p.clientPointId(),
                        "连续两点 " + anchor.getPointTime() + "→" + p.pointTime()
                                + " 等效速度异常，判为 GPS 漂移丢弃，不采信该点"));
                // 关键：不移动锚点，下一点仍以原可信点比对
            } else {
                accepted++;
                if (outside) outsideCount++;
                if (leaveAuthorized) leaveAuthorizedCount++;
                if (hit != null) forbiddenCount++;
                anchor = point;
            }
        }

        if (!toSave.isEmpty()) {
            trackPointRepository.saveAll(toSave);
        }

        // 合并最新位置：全部 ACCEPTED 点中采集时间最大者（漂移点/离线历史点都不会冲回或带偏当前位置）
        List<TrackPoint> allAccepted = trackPointRepository
                .findByOffender_IdAndResultOrderByPointTimeAscIdAsc(obj.getId(), TrackPoint.IngestResult.ACCEPTED);
        TrackPoint latest = allAccepted.isEmpty() ? null
                : allAccepted.get(allAccepted.size() - 1);

        boolean newBreach = false;
        boolean newForbidden = false;
        if (latest != null) {
            obj.setLastLocationAt(latest.getPointTime());
            obj.setLastLat(latest.getLat());
            obj.setLastLng(latest.getLng());
            obj.setLastInsideFence(!latest.getOutsideFence());
            obj.setLastForbidden(Boolean.TRUE.equals(latest.getForbiddenZone()));
            obj.setLastLeaveAuthorized(Boolean.TRUE.equals(latest.getLeaveAuthorized()));
            obj.setLastBattery(latest.getBattery());
            obj.setLastSignal(latest.getSignal());
            obj.setLastWorn(latest.getWorn());
            objectRepository.save(obj);

            boolean countedStatus = obj.getStatus() == CorrectionStatus.SERVING
                    || obj.getStatus() == CorrectionStatus.ADMONISHED
                    || obj.getStatus() == CorrectionStatus.LEAVE;
            if (countedStatus) {
                // 越界红点边沿：准假外出点 outsideFence 已为 false，假期内越界天然不报警；
                // 假期结束（逾期升训诫）后窗口外的越界点恢复报警。
                if (latest.getOutsideFence() && !alreadyOpen(obj.getId(), "GEOFENCE_BREACH")) {
                    violationRepository.save(new ViolationEvent(obj, "GEOFENCE_BREACH",
                            "对象 " + obj.getMaskedName() + " 定位越出「" + obj.getOffice().getName()
                                    + "」活动范围，最近定位时间（" + obj.getOffice().getTimezone() + "）"
                                    + fmtLocal(latest.getPointTime(), fences.zone()), now));
                    newBreach = true;
                }
                // 禁区红点边沿：法定禁区不因请假而解禁，准假期间进入禁区照常报警
                if (Boolean.TRUE.equals(latest.getForbiddenZone())
                        && !alreadyOpen(obj.getId(), "FORBIDDEN_ZONE")) {
                    violationRepository.save(new ViolationEvent(obj, "FORBIDDEN_ZONE",
                            "对象 " + obj.getMaskedName() + " 在禁行时段进入「" + obj.getOffice().getName()
                                    + "」辖区禁区（请假外出不免禁区），最近定位时间（"
                                    + obj.getOffice().getTimezone() + "）"
                                    + fmtLocal(latest.getPointTime(), fences.zone()), now));
                    newForbidden = true;
                }
            }
        }

        return new TrackIngestView(request.points().size(), accepted, duplicates, driftDiscarded,
                rejected.size(), rejected, outsideCount, forbiddenCount, leaveAuthorizedCount,
                obj.getLastLocationAt(), obj.getLastLat(), obj.getLastLng(),
                !Boolean.FALSE.equals(obj.getLastInsideFence()),
                Boolean.TRUE.equals(obj.getLastForbidden()),
                obj.getLastBattery(), obj.getLastSignal(), obj.getLastWorn(),
                newBreach || newForbidden);
    }

    private boolean alreadyOpen(Long offenderId, String type) {
        return violationRepository.findTop20ByOffender_IdOrderByEventTimeDesc(offenderId).stream()
                .filter(v -> type.equals(v.getType()))
                .findFirst()
                .map(v -> !v.getReadFlag())
                .orElse(false);
    }

    static String fmtLocal(Instant at, ZoneId zone) {
        return ZonedDateTime.ofInstant(at, zone).format(LOCAL_FMT);
    }
}
