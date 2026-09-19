package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.*;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.dto.LeaveApplyRequest;
import cn.sfj.jiaowutong.web.dto.LeaveDecisionRequest;
import cn.sfj.jiaowutong.web.vo.LeaveEventView;
import cn.sfj.jiaowutong.web.vo.LeaveView;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 请销假服务。
 *
 * <p><b>两级审批</b>：对象手机端提交 → 司法所初审 → 区司法局复核；任一环节可退回，
 * 对象修改后重提，全过程 {@link LeaveEvent} 流水留痕，退回/重提不覆盖历史。
 *
 * <p><b>与定位联动</b>（裁决权在服务端，不依赖终端）：
 * <ul>
 *   <li>已批准且定位采集时刻落在 [startTime,endTime] 内，越过活动范围围栏属“准假外出”，
 *       相关轨迹点标 leaveAuthorized，<b>不产生越界红点</b>；法定禁区不因此解禁；</li>
 *   <li>假期结束仍未销假：定时任务自动把请假单置 OVERDUE、生成「逾假未归」违规红点，
 *       并按状态机把对象由「请假外出」升为「训诫」，此后越界恢复报警；</li>
 *   <li>区局终批当日起对象状态按状态机置「请假外出」，销假（含逾假后销假）合法回「在矫」。</li>
 * </ul>
 */
@Service
public class LeaveService {

    /** 单次请假最长天数（社区矫正外出请销假上限取 30 天） */
    private static final long MAX_LEAVE_DAYS = 30;
    /** 允许的开始时间相对服务端时钟的提前量（容忍提交耗时/对时误差，秒） */
    private static final long START_SKEW_SEC = 300;

    /** 尚未终结、仍占用“一人一单”的状态 */
    private static final List<LeaveApplication.Status> OPEN_STATUSES = List.of(
            LeaveApplication.Status.PENDING_OFFICE,
            LeaveApplication.Status.PENDING_BUREAU,
            LeaveApplication.Status.RETURNED,
            LeaveApplication.Status.APPROVED,
            LeaveApplication.Status.OVERDUE);

    /** 定位联动：这两个状态下、且时刻在假期窗口内，越出活动范围不算越界 */
    private static final List<LeaveApplication.Status> AWAY_STATUSES = List.of(
            LeaveApplication.Status.APPROVED,
            LeaveApplication.Status.OVERDUE);

    private final LeaveApplicationRepository leaveRepository;
    private final LeaveEventRepository eventRepository;
    private final CorrectionObjectRepository objectRepository;
    private final StatusTransitionRepository transitionRepository;
    private final ViolationEventRepository violationRepository;
    private final AccessControlService accessControl;

    public LeaveService(LeaveApplicationRepository leaveRepository,
                        LeaveEventRepository eventRepository,
                        CorrectionObjectRepository objectRepository,
                        StatusTransitionRepository transitionRepository,
                        ViolationEventRepository violationRepository,
                        AccessControlService accessControl) {
        this.leaveRepository = leaveRepository;
        this.eventRepository = eventRepository;
        this.objectRepository = objectRepository;
        this.transitionRepository = transitionRepository;
        this.violationRepository = violationRepository;
        this.accessControl = accessControl;
    }

    // ============================ 对象端 ============================

    @Transactional
    public LeaveView apply(LeaveApplyRequest req, LoginUser user) {
        assertOffender(user);
        CorrectionObject obj = objectRepository.findById(user.offenderId())
                .orElseThrow(() -> ApiException.notFound("本人档案不存在"));
        if (obj.getStatus() != CorrectionStatus.SERVING) {
            throw ApiException.badRequest("INVALID_STATUS",
                    "当前状态为「" + obj.getStatus().getLabel() + "」，仅在「在矫」状态可发起请假申请");
        }
        validateWindow(req.startTime(), req.endTime(), Instant.now());
        if (leaveRepository.existsByOffender_IdAndStatusIn(obj.getId(), OPEN_STATUSES)) {
            throw ApiException.badRequest("LEAVE_ALREADY_OPEN",
                    "您已有一张在审批或假期中的请假单，不能重复请假；被退回的单子请直接修改后重提");
        }

        Instant now = Instant.now();
        LeaveApplication leave = new LeaveApplication(obj, req.leaveType().trim().toUpperCase(),
                req.reason().trim(), req.destination().trim(), req.startTime(), req.endTime(),
                LeaveApplication.Status.PENDING_OFFICE, now);
        leave = leaveRepository.save(leave);
        record(leave, "SUBMITTED", user.userId(), user.realName(),
                "提交请假申请：" + req.reason().trim());
        return toView(leave);
    }

    /** 退回后修改重提：同一单号延续，新的起止时间重新校验，再从司法所初审走起 */
    @Transactional
    public LeaveView resubmit(Long id, LeaveApplyRequest req, LoginUser user) {
        assertOffender(user);
        LeaveApplication leave = loadOwned(id, user);
        if (leave.getStatus() != LeaveApplication.Status.RETURNED) {
            throw ApiException.badRequest("LEAVE_NOT_RETURNED",
                    "仅「已退回」的请假单可修改重提，当前状态：" + leave.getStatus().getLabel());
        }
        validateWindow(req.startTime(), req.endTime(), Instant.now());

        Instant now = Instant.now();
        leave.setLeaveType(req.leaveType().trim().toUpperCase());
        leave.setReason(req.reason().trim());
        leave.setDestination(req.destination().trim());
        leave.setStartTime(req.startTime());
        leave.setEndTime(req.endTime());
        leave.setStatus(LeaveApplication.Status.PENDING_OFFICE);
        leave.setResubmittedAt(now);
        leave.setReturnedAt(null);
        // 新一轮审批从零走起：清空上轮两级审批结论时间，旧结论仍保留在 LeaveEvent 流水里可回溯
        leave.setOfficeApprovedAt(null);
        leave.setBureauApprovedAt(null);
        leave = leaveRepository.save(leave);
        record(leave, "RESUBMITTED", user.userId(), user.realName(),
                "退回后修改重提：" + req.reason().trim());
        return toView(leave);
    }

    /** 终批前对象主动撤回 */
    @Transactional
    public LeaveView withdraw(Long id, LoginUser user) {
        assertOffender(user);
        LeaveApplication leave = loadOwned(id, user);
        if (leave.getStatus() != LeaveApplication.Status.PENDING_OFFICE
                && leave.getStatus() != LeaveApplication.Status.PENDING_BUREAU
                && leave.getStatus() != LeaveApplication.Status.RETURNED) {
            throw ApiException.badRequest("LEAVE_NOT_WITHDRAWABLE",
                    "已批准的请假单不能撤回，请直接办理销假；当前状态：" + leave.getStatus().getLabel());
        }
        leave.setStatus(LeaveApplication.Status.CANCELLED);
        leave = leaveRepository.save(leave);
        record(leave, "WITHDRAWN", user.userId(), user.realName(), "对象主动撤回请假申请");
        return toView(leave);
    }

    /** 对象手机端销假：假期内/逾假后均可，逾假违规红点保留 */
    @Transactional
    public LeaveView returnCheckin(Long id, String note, LoginUser user) {
        assertOffender(user);
        LeaveApplication leave = loadOwned(id, user);
        doReturnCheckin(leave, user.userId(), user.realName(),
                note == null || note.isBlank() ? "对象手机端销假返所" : note.trim());
        return toView(leave);
    }

    /** 管理端代登记当面销假（本所干警 / 区局） */
    @Transactional
    public LeaveView staffReturnCheckin(Long id, String note, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        LeaveApplication leave = loadVisible(id, user);
        doReturnCheckin(leave, user.userId(), user.realName(),
                "干警代登记当面销假" + (note == null || note.isBlank() ? "" : "：" + note.trim()));
        return toView(leave);
    }

    private void doReturnCheckin(LeaveApplication leave, Long operatorId, String operatorName, String comment) {
        if (leave.getStatus() != LeaveApplication.Status.APPROVED
                && leave.getStatus() != LeaveApplication.Status.OVERDUE) {
            throw ApiException.badRequest("LEAVE_NOT_ACTIVE",
                    "仅已批准（假期中或已逾期）的请假单可销假，当前状态：" + leave.getStatus().getLabel());
        }
        CorrectionObject obj = leave.getOffender();
        Instant now = Instant.now();
        boolean overdue = leave.getStatus() == LeaveApplication.Status.OVERDUE
                || Boolean.TRUE.equals(leave.getOverdueFlag())
                || now.isAfter(leave.getEndTime());

        // 状态机合法回「在矫」：请假外出→在矫；逾假已升训诫→教育恢复在矫，均为合法路径
        CorrectionStateMachine.assertTransition(obj.getStatus(), CorrectionStatus.SERVING);
        CorrectionStatus from = obj.getStatus();
        obj.setStatus(CorrectionStatus.SERVING);
        objectRepository.save(obj);
        transitionRepository.save(new StatusTransition(obj.getId(), from, CorrectionStatus.SERVING,
                operatorId, operatorName, overdue
                ? "逾假后销假返所，恢复在矫（逾假未归违规已记录并保留）"
                : "按期销假返所"));

        leave.setStatus(LeaveApplication.Status.COMPLETED);
        leave.setActualReturnAt(now);
        leaveRepository.save(leave);
        record(leave, "RETURN_CHECKIN", operatorId, operatorName, comment);
    }

    @Transactional(readOnly = true)
    public List<LeaveView> myLeaves(LoginUser user) {
        assertOffender(user);
        return leaveRepository.findByOffender_IdOrderByCreatedAtDescIdDesc(user.offenderId()).stream()
                .map(this::toView).toList();
    }

    // ============================ 管理端（两级审批） ============================

    /** 司法所初审工作台：本所干警看待初审；区局可看全部待初审 */
    @Transactional(readOnly = true)
    public List<LeaveView> pendingOffice(LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        List<LeaveApplication> list = leaveRepository
                .findByStatusOrderByOfficeApprovedAtAscIdAsc(LeaveApplication.Status.PENDING_OFFICE);
        return list.stream()
                .filter(l -> user.role() == Role.SUPERVISOR
                        || user.officeId().equals(l.getOfficeId()))
                .map(this::toView).toList();
    }

    /** 区局复核工作台：仅区局监管员可访问 */
    @Transactional(readOnly = true)
    public List<LeaveView> pendingBureau(LoginUser user) {
        assertSupervisor(user);
        return leaveRepository
                .findByStatusOrderByOfficeApprovedAtAscIdAsc(LeaveApplication.Status.PENDING_BUREAU)
                .stream().map(this::toView).toList();
    }

    /** 审批总览：审批中 + 假期中（区局看全部；本所干警只看本所） */
    @Transactional(readOnly = true)
    public List<LeaveView> listActive(LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        return leaveRepository.findByStatusInOrderByCreatedAtDescIdDesc(OPEN_STATUSES).stream()
                .filter(l -> user.role() == Role.SUPERVISOR || user.officeId().equals(l.getOfficeId()))
                .map(this::toView).toList();
    }

    /** 司法所初审：通过转区局复核；退回对象修改重提（理由必填，留痕） */
    @Transactional
    public LeaveView officeDecide(Long id, LeaveDecisionRequest req, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        LeaveApplication leave = loadVisible(id, user);
        if (user.role() == Role.STAFF && !user.officeId().equals(leave.getOfficeId())) {
            throw ApiException.forbidden("司法所初审须由对象归属司法所干警办理，不能跨所审批");
        }
        if (leave.getStatus() != LeaveApplication.Status.PENDING_OFFICE) {
            throw ApiException.badRequest("LEAVE_WRONG_STAGE",
                    "该单不在「待司法所初审」环节，当前状态：" + leave.getStatus().getLabel());
        }
        Instant now = Instant.now();
        if (Boolean.TRUE.equals(req.approve())) {
            leave.setStatus(LeaveApplication.Status.PENDING_BUREAU);
            leave.setOfficeApprovedAt(now);
            leaveRepository.save(leave);
            record(leave, "OFFICE_APPROVED", user.userId(), user.realName(),
                    commentOr(req.comment(), "司法所初审同意，报区司法局复核"));
        } else {
            requireReturnReason(req.comment());
            leave.setStatus(LeaveApplication.Status.RETURNED);
            leave.setReturnedAt(now);
            leaveRepository.save(leave);
            record(leave, "OFFICE_RETURNED", user.userId(), user.realName(), req.comment().trim());
        }
        return toView(leave);
    }

    /** 区局复核：通过即终批准假（对象置请假外出）；退回对象修改重提 */
    @Transactional
    public LeaveView bureauDecide(Long id, LeaveDecisionRequest req, LoginUser user) {
        assertSupervisor(user);
        LeaveApplication leave = loadVisible(id, user);
        CorrectionObject obj = leave.getOffender();
        if (leave.getStatus() != LeaveApplication.Status.PENDING_BUREAU) {
            throw ApiException.badRequest("LEAVE_WRONG_STAGE",
                    "该单不在「待区局复核」环节，当前状态：" + leave.getStatus().getLabel()
                            + "；区局不能覆盖司法所初审或已终批的结论，请刷新后按当前环节办理");
        }
        Instant now = Instant.now();
        if (Boolean.TRUE.equals(req.approve())) {
            // 终批：按状态机 SERVING→LEAVE，写状态流转留痕
            CorrectionStateMachine.assertTransition(obj.getStatus(), CorrectionStatus.LEAVE);
            CorrectionStatus from = obj.getStatus();
            obj.setStatus(CorrectionStatus.LEAVE);
            objectRepository.save(obj);
            transitionRepository.save(new StatusTransition(obj.getId(), from, CorrectionStatus.LEAVE,
                    user.userId(), user.realName(),
                    "两级审批通过（司法所初审+区局复核）：" + typeLabel(leave.getLeaveType())
                            + "，" + leave.getStartTime() + " 至 " + leave.getEndTime()
                            + "，目的地：" + leave.getDestination()));

            leave.setStatus(LeaveApplication.Status.APPROVED);
            leave.setBureauApprovedAt(now);
            leaveRepository.save(leave);
            record(leave, "BUREAU_APPROVED", user.userId(), user.realName(),
                    commentOr(req.comment(), "区司法局复核通过，准予外出"));
        } else {
            requireReturnReason(req.comment());
            leave.setStatus(LeaveApplication.Status.RETURNED);
            leave.setReturnedAt(now);
            // 初审通过标记暂保留，对象重提时会与区局终批标记一并清空，从头走两级；本轮退回流水单独留痕
            leaveRepository.save(leave);
            record(leave, "BUREAU_RETURNED", user.userId(), user.realName(), req.comment().trim());
        }
        return toView(leave);
    }

    @Transactional(readOnly = true)
    public LeaveView detail(Long id, LoginUser user) {
        return toView(loadVisible(id, user));
    }

    // ============================ 定位联动 / 逾期定时 ============================

    /**
     * 对象在某 UTC 定位时刻是否处于“准假外出窗口”：已批准（含已逾期单在窗口内）
     * 且 startTime ≤ at ≤ endTime。轨迹/报到判定越界前调用，命中则越出活动范围不算越界。
     */
    @Transactional(readOnly = true)
    public boolean isOnAuthorizedLeaveAt(Long offenderId, Instant at) {
        return activeLeaveAt(offenderId, at).isPresent();
    }

    /** 批量判定用：一次取对象全部准假单，逐点在内存判窗口（避免逐点查库） */
    @Transactional(readOnly = true)
    public List<LeaveApplication> loadAwayLeaves(Long offenderId) {
        return leaveRepository.findAll().stream()
                .filter(l -> l.getOffender().getId().equals(offenderId))
                .filter(l -> AWAY_STATUSES.contains(l.getStatus()))
                .toList();
    }

    /** 单个时刻是否落在任一准假窗口 [start,end]（含起止边界） */
    public static boolean withinAnyLeave(List<LeaveApplication> leaves, Instant at) {
        for (LeaveApplication l : leaves) {
            if (!at.isBefore(l.getStartTime()) && !at.isAfter(l.getEndTime())) {
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public Optional<LeaveApplication> activeLeaveAt(Long offenderId, Instant at) {
        return leaveRepository.findAll().stream()
                .filter(l -> l.getOffender().getId().equals(offenderId))
                .filter(l -> AWAY_STATUSES.contains(l.getStatus()))
                .filter(l -> !at.isBefore(l.getStartTime()) && !at.isAfter(l.getEndTime()))
                .findFirst();
    }

    /** 批量：返回在 at 时刻处于准假窗口内的对象 id 集合（作战台“今日应报到”跳过这些对象） */
    @Transactional(readOnly = true)
    public java.util.Set<Long> offenderIdsOnLeaveAt(Instant at) {
        return leaveRepository.findAll().stream()
                .filter(l -> AWAY_STATUSES.contains(l.getStatus()))
                .filter(l -> !at.isBefore(l.getStartTime()) && !at.isAfter(l.getEndTime()))
                .map(l -> l.getOffender().getId())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 定时扫描：已批准且截止时刻已过仍未销假 → 自动升违规。
     * 每 60 秒一次；服务启动 20 秒后先跑一遍。findByStatusAndEndTimeBefore 带悲观锁，
     * 多实例部署不会重复升违规。
     *
     * <p>幂等边界：只扫「已批准·假期中」（APPROVED）且 now &gt; endTime 的单。
     * <ul>
     *   <li>COMPLETED（按期或逾假后已销假）一律不扫——已销假不能再被翻案成逾假；</li>
     *   <li>OVERDUE 已在上一轮处理过，不重复生成违规红点/流水、不重复升训诫。</li>
     * </ul>
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 20_000L)
    @Transactional
    public void sweepOverdueLeaves() {
        Instant now = Instant.now();
        List<LeaveApplication> due =
                leaveRepository.findByStatusAndEndTimeBefore(LeaveApplication.Status.APPROVED, now);
        for (LeaveApplication leave : due) {
            markOverdue(leave, now);
        }
    }

    private void markOverdue(LeaveApplication leave, Instant now) {
        // 双保险幂等：只允许「已批准·假期中」、未登记过逾期、确实已过截止时刻的单进入处置，
        // 已销假（COMPLETED）/已逾假（OVERDUE）直接跳过，绝不重复登记或翻案。
        if (leave.getStatus() != LeaveApplication.Status.APPROVED
                || Boolean.TRUE.equals(leave.getOverdueFlag())
                || !leave.getEndTime().isBefore(now)) {
            return;
        }
        CorrectionObject obj = leave.getOffender();
        leave.setStatus(LeaveApplication.Status.OVERDUE);
        leave.setOverdueFlag(true);
        leaveRepository.save(leave);
        record(leave, "SYSTEM_OVERDUE", 0L, "系统（逾期巡检）",
                "假期截止 " + leave.getEndTime() + " 仍未销假，自动登记逾假未归违规");

        violationRepository.save(new ViolationEvent(obj, "LEAVE_OVERDUE",
                "对象 " + obj.getMaskedName() + " 请假至 " + leave.getEndTime()
                        + " 已到期但未销假，系统按逾假未归自动升为违规（请假单号 #" + leave.getId() + "）",
                now));

        // 仍在「请假外出」则按状态机升「训诫」；若状态已被人工改动则不强行跳转
        if (obj.getStatus() == CorrectionStatus.LEAVE) {
            CorrectionStateMachine.assertTransition(CorrectionStatus.LEAVE, CorrectionStatus.ADMONISHED);
            obj.setStatus(CorrectionStatus.ADMONISHED);
            objectRepository.save(obj);
            transitionRepository.save(new StatusTransition(obj.getId(),
                    CorrectionStatus.LEAVE, CorrectionStatus.ADMONISHED,
                    0L, "系统（逾期巡检）",
                    "假期结束未销假，逾假未归，系统自动升为违规（训诫）；事后销假可恢复在矫"));
        }
    }

    // ============================ 内部工具 ============================

    private void validateWindow(Instant start, Instant end, Instant now) {
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("INVALID_LEAVE_WINDOW", "假期截止时间必须晚于开始时间");
        }
        if (start.isBefore(now.minusSeconds(START_SKEW_SEC))) {
            throw ApiException.badRequest("INVALID_LEAVE_WINDOW",
                    "假期开始时间不能早于当前时间，请如实选择外出开始时刻");
        }
        long days = ChronoUnit.DAYS.between(start, end);
        if (days > MAX_LEAVE_DAYS) {
            throw ApiException.badRequest("INVALID_LEAVE_WINDOW",
                    "单次请假不得超过 " + MAX_LEAVE_DAYS + " 天，更长假期请到司法所当面报批");
        }
    }

    private void requireReturnReason(String comment) {
        if (comment == null || comment.trim().length() < 4) {
            throw ApiException.badRequest("RETURN_REASON_REQUIRED",
                    "退回必须填写不少于 4 个字的退回意见，对象将据此修改并重提，该意见全程留痕");
        }
    }

    private String commentOr(String comment, String fallback) {
        return comment == null || comment.isBlank() ? fallback : comment.trim();
    }

    private void record(LeaveApplication leave, String action, Long operatorId,
                        String operatorName, String comment) {
        eventRepository.save(new LeaveEvent(leave.getId(), action, leave.getStatus(),
                operatorId, operatorName, comment, Instant.now()));
    }

    private LeaveApplication loadOwned(Long id, LoginUser user) {
        LeaveApplication leave = leaveRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("请假单不存在（#" + id + "）"));
        if (!leave.getOffender().getId().equals(user.offenderId())) {
            throw ApiException.forbidden("对象间数据相互隔离：您只能操作本人的请假单");
        }
        return leave;
    }

    private LeaveApplication loadVisible(Long id, LoginUser user) {
        LeaveApplication leave = leaveRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("请假单不存在（#" + id + "）"));
        accessControl.assertCanView(leave.getOffender(), user);
        return leave;
    }

    private void assertOffender(LoginUser user) {
        if (user.role() != Role.OFFENDER || user.offenderId() == null) {
            throw ApiException.forbidden("仅矫正对象本人账号可办理请假/销假");
        }
    }

    private void assertSupervisor(LoginUser user) {
        if (user.role() != Role.SUPERVISOR) {
            throw ApiException.forbidden("请假终批复核权限属区司法局（监管员），司法所干警完成初审即可");
        }
    }

    @Transactional(readOnly = true)
    public LeaveView toView(LeaveApplication leave) {
        CorrectionObject o = leave.getOffender();
        List<LeaveEventView> events = eventRepository
                .findByLeaveIdOrderByOccurredAtAscIdAsc(leave.getId()).stream()
                .map(e -> new LeaveEventView(e.getId(), e.getAction(), actionLabel(e.getAction()),
                        e.getResultStatus(), statusLabel(e.getResultStatus()),
                        e.getOperatorId(), e.getOperatorName(), e.getComment(), e.getOccurredAt()))
                .toList();
        return new LeaveView(
                leave.getId(), o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                o.getOffice().getId(), o.getOffice().getName(), o.getOffice().getTimezone(),
                leave.getLeaveType(), typeLabel(leave.getLeaveType()),
                leave.getReason(), leave.getDestination(),
                leave.getStartTime(), leave.getEndTime(),
                leave.getStatus().name(), leave.getStatus().getLabel(),
                leave.getSubmittedAt(), leave.getOfficeApprovedAt(), leave.getBureauApprovedAt(),
                leave.getReturnedAt(), leave.getResubmittedAt(), leave.getActualReturnAt(),
                leave.getOverdueFlag(), events);
    }

    static String typeLabel(String t) {
        return switch (t == null ? "" : t) {
            case "PERSONAL" -> "事假";
            case "SICK" -> "病假";
            case "OTHER" -> "其他";
            default -> t;
        };
    }

    static String statusLabel(String name) {
        try {
            return LeaveApplication.Status.valueOf(name).getLabel();
        } catch (Exception e) {
            return name;
        }
    }

    static String actionLabel(String action) {
        return switch (action) {
            case "SUBMITTED" -> "提交申请";
            case "RESUBMITTED" -> "退回后重提";
            case "OFFICE_APPROVED" -> "司法所初审通过";
            case "OFFICE_RETURNED" -> "司法所初审退回";
            case "BUREAU_APPROVED" -> "区局复核通过（终批）";
            case "BUREAU_RETURNED" -> "区局复核退回";
            case "WITHDRAWN" -> "对象撤回";
            case "RETURN_CHECKIN" -> "销假返所";
            case "SYSTEM_OVERDUE" -> "系统逾假判定";
            default -> action;
        };
    }
}
