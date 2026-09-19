package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.*;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.dto.ActivityPublishRequest;
import cn.sfj.jiaowutong.web.dto.ActivityPunchRequest;
import cn.sfj.jiaowutong.web.vo.ActivityView;
import cn.sfj.jiaowutong.web.vo.AttendanceView;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公益活动（社区服务）：
 * <ol>
 *   <li>司法所/区局发布活动（带活动点中心+核验半径与打卡时段）；</li>
 *   <li>对象手机端报名（限本所、限在矫、限名额、一人一次、活动开始前可取消）；</li>
 *   <li>现场打卡：服务端用当前定位重算到活动点距离，≤半径判 NORMAL，
 *       超出半径仍如实记 ABNORMAL（不丢弃）并生成异常打卡违规红点，杜绝“人没到也打卡”；</li>
 *   <li>管理端花名册看报名/打卡结果，异常打卡逐条可查。</li>
 * </ol>
 */
@Service
public class PublicActivityService {

    /** 实时打卡定位允许的时钟偏移（分钟），与报到/轨迹一致 */
    private static final long REALTIME_SKEW_MIN = 5;
    private static final long FUTURE_SKEW_SEC = 120;

    /** 可参加公益活动的在矫状态（请假外出期间人不在辖区，不能报名/打卡） */
    private static final Set<CorrectionStatus> ATTENDABLE =
            EnumSet.of(CorrectionStatus.SERVING, CorrectionStatus.ADMONISHED);

    private final PublicActivityRepository activityRepository;
    private final ActivityEnrollmentRepository enrollmentRepository;
    private final ActivityAttendanceRepository attendanceRepository;
    private final JudicialOfficeRepository officeRepository;
    private final ViolationEventRepository violationRepository;
    private final AccessControlService accessControl;

    public PublicActivityService(PublicActivityRepository activityRepository,
                                 ActivityEnrollmentRepository enrollmentRepository,
                                 ActivityAttendanceRepository attendanceRepository,
                                 JudicialOfficeRepository officeRepository,
                                 ViolationEventRepository violationRepository,
                                 AccessControlService accessControl) {
        this.activityRepository = activityRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.officeRepository = officeRepository;
        this.violationRepository = violationRepository;
        this.accessControl = accessControl;
    }

    // ============================ 发布 / 管理 ============================

    @Transactional
    public ActivityView publish(ActivityPublishRequest req, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        JudicialOffice office;
        if (user.role() == Role.SUPERVISOR) {
            Long targetOfficeId = req.officeId() == null ? user.officeId() : req.officeId();
            if (targetOfficeId == null) {
                throw ApiException.badRequest("OFFICE_REQUIRED", "区局发布活动须指定归属司法所");
            }
            office = officeRepository.findById(targetOfficeId)
                    .orElseThrow(() -> ApiException.badRequest("OFFICE_REQUIRED", "指定的司法所不存在"));
        } else {
            // 司法所干警只能发到本所，忽略请求里的 officeId，防越权发到别的所
            office = officeRepository.findById(user.officeId())
                    .orElseThrow(() -> ApiException.badRequest("OFFICE_REQUIRED", "干警账号未绑定司法所"));
        }
        validateWindow(req.startTime(), req.endTime());

        PublicActivity a = new PublicActivity(office, req.title().trim(), req.detail().trim(),
                req.address().trim(), req.lat(), req.lng(), req.radiusMeters(),
                req.startTime(), req.endTime(), req.capacity(),
                user.userId(), user.realName(), Instant.now());
        a = activityRepository.save(a);
        return toStaffView(a, 0L, null);
    }

    /** 取消活动（不删除，保留报名/打卡留痕） */
    @Transactional
    public ActivityView cancel(Long id, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        PublicActivity a = loadManageable(id, user);
        a.setStatus("CANCELLED");
        activityRepository.save(a);
        return toStaffView(a, enrolledCount(a), null);
    }

    @Transactional(readOnly = true)
    public List<ActivityView> listStaff(LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        List<PublicActivity> all = activityRepository.findAllByOrderByStartTimeDescIdDesc();
        return all.stream()
                .filter(a -> user.role() == Role.SUPERVISOR || user.officeId().equals(a.getOffice().getId()))
                .map(a -> toStaffView(a, enrolledCount(a), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public ActivityView staffDetail(Long id, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        PublicActivity a = loadManageable(id, user);
        return toStaffView(a, enrolledCount(a), null);
    }

    /** 花名册：报名人员 + 打卡结果（含异常） */
    @Transactional(readOnly = true)
    public List<AttendanceView> roster(Long id, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        PublicActivity a = loadManageable(id, user);

        Map<Long, ActivityAttendance> punchByOffender = attendanceRepository
                .findByActivity_IdOrderByRecordedAtAscIdAsc(id).stream()
                .collect(Collectors.toMap(p -> p.getOffender().getId(), p -> p, (x, y) -> x));

        return enrollmentRepository.findByActivity_IdOrderByEnrolledAtAscIdAsc(id).stream()
                .map(e -> {
                    CorrectionObject o = e.getOffender();
                    ActivityAttendance p = punchByOffender.get(o.getId());
                    return new AttendanceView(
                            p == null ? null : p.getId(), a.getId(),
                            o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                            e.getStatus(),
                            p == null ? null : p.getResult(),
                            p == null ? null : p.getPunchTime(),
                            p == null ? null : p.getRecordedAt(),
                            p == null ? null : p.getLat(),
                            p == null ? null : p.getLng(),
                            p == null ? null : p.getDistanceMeters(),
                            p == null ? null : p.getRadiusMeters());
                })
                .toList();
    }

    // ============================ 对象手机端 ============================

    @Transactional(readOnly = true)
    public List<ActivityView> listForOffender(LoginUser user) {
        assertOffender(user);
        CorrectionObject me = loadSelf(user);
        Instant now = Instant.now();
        return activityRepository.findByOfficeIdAndStatusOrderByStartTimeAscIdDesc(
                        me.getOffice().getId(), "PUBLISHED").stream()
                .map(a -> toOffenderView(a, me, now))
                .toList();
    }

    @Transactional
    public ActivityView enroll(Long id, LoginUser user) {
        assertOffender(user);
        CorrectionObject me = loadSelf(user);
        PublicActivity a = activityRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("活动不存在或已删除"));
        assertSameOffice(a, me);
        Instant now = Instant.now();

        if (!"PUBLISHED".equals(a.getStatus())) {
            throw ApiException.badRequest("ACTIVITY_NOT_OPEN", "活动已" + statusText(a.getStatus()) + "，不能报名");
        }
        if (!ATTENDABLE.contains(me.getStatus())) {
            throw ApiException.badRequest("INVALID_STATUS",
                    "当前状态为「" + me.getStatus().getLabel() + "」，不在可参加公益活动的在矫状态");
        }
        if (!now.isBefore(a.getStartTime())) {
            throw ApiException.badRequest("ENROLLMENT_CLOSED", "活动已开始或已结束，报名通道已关闭");
        }
        ActivityEnrollment existing = enrollmentRepository
                .findByActivity_IdAndOffender_Id(id, me.getId()).orElse(null);
        if (existing != null && "ENROLLED".equals(existing.getStatus())) {
            throw ApiException.badRequest("ALREADY_ENROLLED", "您已报名该活动，无需重复报名");
        }
        if (isFull(a)) {
            throw ApiException.badRequest("ACTIVITY_FULL", "活动名额已满，请联系司法所或选择其他场次");
        }

        if (existing == null) {
            enrollmentRepository.save(new ActivityEnrollment(a, me, now));
        } else {
            // 此前取消过：重新报名
            existing.setStatus("ENROLLED");
            existing.setCancelledAt(null);
            enrollmentRepository.save(existing);
        }
        return toOffenderView(a, me, now);
    }

    @Transactional
    public ActivityView cancelEnrollment(Long id, LoginUser user) {
        assertOffender(user);
        CorrectionObject me = loadSelf(user);
        PublicActivity a = activityRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("活动不存在或已删除"));
        ActivityEnrollment e = enrollmentRepository.findByActivity_IdAndOffender_Id(id, me.getId())
                .orElseThrow(() -> ApiException.badRequest("NOT_ENROLLED", "您未报名该活动，无需取消"));
        if (!"ENROLLED".equals(e.getStatus())) {
            throw ApiException.badRequest("NOT_ENROLLED", "报名已取消，不能重复取消");
        }
        if (!Instant.now().isBefore(a.getStartTime())) {
            throw ApiException.badRequest("ENROLLMENT_LOCKED", "活动已开始，报名不可取消；确不能到场请联系司法所");
        }
        e.setStatus("CANCELLED");
        e.setCancelledAt(Instant.now());
        enrollmentRepository.save(e);
        return toOffenderView(a, me, Instant.now());
    }

    /**
     * 现场打卡：时效/时间窗/报名/重复全部服务端校验，是否落在活动点范围内由服务端几何重算。
     * 范围外不拒绝、不丢弃，记 ABNORMAL 并出异常红点；返回体带实测距离与结论。
     */
    @Transactional
    public Map<String, Object> punch(Long id, ActivityPunchRequest req, LoginUser user) {
        assertOffender(user);
        CorrectionObject me = loadSelf(user);
        PublicActivity a = activityRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("活动不存在或已删除"));
        assertSameOffice(a, me);
        Instant now = Instant.now();

        if (!"PUBLISHED".equals(a.getStatus())) {
            throw ApiException.badRequest("ACTIVITY_NOT_OPEN", "活动已" + statusText(a.getStatus()) + "，不能打卡");
        }
        ActivityEnrollment e = enrollmentRepository.findByActivity_IdAndOffender_Id(id, me.getId())
                .orElseThrow(() -> ApiException.badRequest("NOT_ENROLLED", "您未报名该活动，请先报名再现场打卡"));
        if (!"ENROLLED".equals(e.getStatus())) {
            throw ApiException.badRequest("NOT_ENROLLED", "报名已取消，不能打卡；如需参加请重新报名");
        }
        if (attendanceRepository.existsByActivity_IdAndOffender_Id(id, me.getId())) {
            throw ApiException.badRequest("ALREADY_PUNCHED", "您已完成本次活动打卡，无需重复打卡");
        }
        // 定位时效：与报到一致，禁止用缓存旧位置
        if (req.fixTime().isAfter(now.plusSeconds(FUTURE_SKEW_SEC))) {
            throw ApiException.badRequest("STALE_LOCATION", "打卡定位时间晚于当前时间，疑似伪造定位");
        }
        if (req.fixTime().isBefore(now.minusSeconds(REALTIME_SKEW_MIN * 60))) {
            throw ApiException.badRequest("STALE_LOCATION",
                    "打卡定位采集于 " + req.fixTime() + "，已超过 " + REALTIME_SKEW_MIN
                            + " 分钟时效。请到活动现场后重新获取当前定位再打卡，不能用缓存旧位置");
        }
        // 打卡时间窗按定位采集时刻判（UTC 直比，活动时间窗本身以 UTC 存储）
        if (req.fixTime().isBefore(a.getStartTime()) || req.fixTime().isAfter(a.getEndTime())) {
            throw ApiException.badRequest("OUT_OF_PUNCH_WINDOW",
                    "当前不在活动打卡时段内（" + a.getStartTime() + " 至 " + a.getEndTime() + "，UTC），不能提前或事后补打卡");
        }

        double dist = GeoUtil.distanceMeters(req.lat(), req.lng(), a.getLat(), a.getLng());
        boolean within = dist <= a.getRadiusMeters();
        String result = within ? "NORMAL" : "ABNORMAL";

        ActivityAttendance att = new ActivityAttendance(a, me, result, req.fixTime(), now,
                req.lat(), req.lng(), Math.round(dist * 10) / 10.0d, a.getRadiusMeters());
        attendanceRepository.save(att);

        String message;
        if (within) {
            message = "打卡成功：定位距活动点 " + fmtDist(dist) + "，在 " + a.getRadiusMeters()
                    + " 米核验范围内，记为正常参加";
        } else {
            message = "打卡已记录但判定异常：定位距活动点 " + fmtDist(dist) + "，超出 "
                    + a.getRadiusMeters() + " 米核验范围，已标记异常并通知司法所核查";
            violationRepository.save(new ViolationEvent(me, "ACTIVITY_ABNORMAL",
                    "对象 " + me.getMaskedName() + " 在公益活动「" + a.getTitle()
                            + "」打卡时定位偏离活动点 " + fmtDist(dist) + "（允许半径 "
                            + a.getRadiusMeters() + " 米），现场打卡位置核验不通过", now));
        }

        return Map.of(
                "punched", true,
                "result", result,
                "distanceMeters", Math.round(dist * 10) / 10.0d,
                "radiusMeters", a.getRadiusMeters(),
                "withinRange", within,
                "activityTitle", a.getTitle(),
                "punchTime", req.fixTime(),
                "message", message);
    }

    // ============================ 定时归档 ============================

    /** 已过结束时间仍处 PUBLISHED 的活动自动置 FINISHED（每 5 分钟巡检） */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 30_000L)
    @Transactional
    public void sweepFinishedActivities() {
        Instant now = Instant.now();
        activityRepository.findByStatusOrderByStartTimeAscIdAsc("PUBLISHED").stream()
                .filter(a -> now.isAfter(a.getEndTime()))
                .forEach(a -> a.setStatus("FINISHED"));
    }

    // ============================ 视图组装 ============================

    private ActivityView toOffenderView(PublicActivity a, CorrectionObject me, Instant now) {
        long enrolled = enrolledCount(a);
        ActivityEnrollment e = enrollmentRepository
                .findByActivity_IdAndOffender_Id(a.getId(), me.getId()).orElse(null);
        String myEnroll = e == null ? null : e.getStatus();
        ActivityAttendance p = attendanceRepository
                .findByActivity_IdAndOffender_Id(a.getId(), me.getId()).orElse(null);

        boolean open = "PUBLISHED".equals(a.getStatus());
        boolean enrollable = open && ATTENDABLE.contains(me.getStatus())
                && now.isBefore(a.getStartTime())
                && (e == null || "CANCELLED".equals(e.getStatus()))
                && !isFull(a, enrolled);
        boolean cancellable = open && e != null && "ENROLLED".equals(e.getStatus())
                && now.isBefore(a.getStartTime());
        boolean punchable = open && e != null && "ENROLLED".equals(e.getStatus())
                && !now.isBefore(a.getStartTime()) && !now.isAfter(a.getEndTime())
                && p == null;

        return new ActivityView(a.getId(), a.getOffice().getId(), a.getOffice().getName(),
                a.getOffice().getTimezone(), a.getTitle(), a.getDetail(), a.getAddress(),
                a.getLat(), a.getLng(), a.getRadiusMeters(), a.getStartTime(), a.getEndTime(),
                a.getCapacity(), a.getStatus(), enrolled, isFull(a, enrolled),
                myEnroll, p == null ? null : p.getResult(),
                p == null ? null : p.getDistanceMeters(),
                enrollable, cancellable, punchable);
    }

    private ActivityView toStaffView(PublicActivity a, long enrolled, String myEnroll) {
        return new ActivityView(a.getId(), a.getOffice().getId(), a.getOffice().getName(),
                a.getOffice().getTimezone(), a.getTitle(), a.getDetail(), a.getAddress(),
                a.getLat(), a.getLng(), a.getRadiusMeters(), a.getStartTime(), a.getEndTime(),
                a.getCapacity(), a.getStatus(), enrolled, isFull(a, enrolled),
                myEnroll, null, null, false, false, false);
    }

    // ============================ 工具 ============================

    private void validateWindow(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("INVALID_WINDOW", "活动结束时间必须晚于开始时间");
        }
        if (start.isBefore(Instant.now().minusSeconds(60))) {
            throw ApiException.badRequest("INVALID_WINDOW", "活动开始时间不能早于当前时间");
        }
    }

    private PublicActivity loadManageable(Long id, LoginUser user) {
        PublicActivity a = activityRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("活动不存在或已删除（#" + id + "）"));
        if (user.role() == Role.STAFF && !user.officeId().equals(a.getOffice().getId())) {
            throw ApiException.forbidden("该活动归属「" + a.getOffice().getName() + "」，不在您所在司法所管辖范围");
        }
        return a;
    }

    private void assertSameOffice(PublicActivity a, CorrectionObject me) {
        if (!a.getOffice().getId().equals(me.getOffice().getId())) {
            throw ApiException.forbidden("对象间/所际数据隔离：该活动不面向您所在司法所发布，不能报名或打卡");
        }
    }

    private CorrectionObject loadSelf(LoginUser user) {
        return accessControl.loadVisible(user.offenderId(), user);
    }

    private void assertOffender(LoginUser user) {
        if (user.role() != Role.OFFENDER || user.offenderId() == null) {
            throw ApiException.forbidden("仅矫正对象本人账号可报名或现场打卡");
        }
    }

    private long enrolledCount(PublicActivity a) {
        return enrollmentRepository.countByActivity_IdAndStatus(a.getId(), "ENROLLED");
    }

    private boolean isFull(PublicActivity a) {
        return isFull(a, enrolledCount(a));
    }

    private boolean isFull(PublicActivity a, long enrolled) {
        return a.getCapacity() != null && enrolled >= a.getCapacity();
    }

    private String statusText(String status) {
        return switch (status) {
            case "FINISHED" -> "结束";
            case "CANCELLED" -> "取消";
            default -> "关闭";
        };
    }

    private String fmtDist(double meters) {
        return meters >= 1000 ? (Math.round(meters / 100) / 10.0d) + " 公里"
                : Math.round(meters) + " 米";
    }
}
