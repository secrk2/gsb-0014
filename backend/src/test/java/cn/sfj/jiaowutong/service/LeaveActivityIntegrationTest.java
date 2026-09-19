package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.*;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.dto.ActivityPublishRequest;
import cn.sfj.jiaowutong.web.dto.ActivityPunchRequest;
import cn.sfj.jiaowutong.web.dto.LeaveApplyRequest;
import cn.sfj.jiaowutong.web.dto.LeaveDecisionRequest;
import cn.sfj.jiaowutong.web.dto.TrackBatchRequest;
import cn.sfj.jiaowutong.web.vo.LeaveView;
import cn.sfj.jiaowutong.web.vo.MonthlyReportResultView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请销假 + 公益活动 + 月度报到 端到端集成测试（基于种子空库启动）：
 * 1. 两级审批：对象提交 → 司法所初审 → 区局复核终批，对象置请假外出；
 * 2. 定位联动：准假窗口内几何越界点标 leaveAuthorized、不报越界红点；
 * 3. 逾期：到期未销假由巡检升 OVERDUE/训诫/违规，事后销假恢复在矫；
 * 4. 公益活动报名 + 现场打卡：范围内 NORMAL、范围外 ABNORMAL 并出违规红点、未报名拒绝；
 * 5. 月度批量报到：部分对象（终态/入矫登记）失败，逐条给出原因，入参去重。
 */
@SpringBootTest
class LeaveActivityIntegrationTest {

    @Autowired private LeaveService leaveService;
    @Autowired private PublicActivityService activityService;
    @Autowired private MonthlyReportService monthlyReportService;
    @Autowired private TrackService trackService;
    @Autowired private CorrectionObjectRepository objectRepository;
    @Autowired private JudicialOfficeRepository officeRepository;
    @Autowired private LeaveApplicationRepository leaveRepository;
    @Autowired private ViolationEventRepository violationRepository;
    @Autowired private TrackPointRepository trackPointRepository;

    private LoginUser staff(String code) {
        JudicialOffice o = officeRepository.findAll().stream()
                .filter(x -> x.getCode().equals(code)).findFirst().orElseThrow();
        return new LoginUser(o.getId(), "staff-" + code, "干警" + code, Role.STAFF, o.getId(), null);
    }

    private LoginUser supervisor() {
        return new LoginUser(999L, "jiandu-test", "陈督导", Role.SUPERVISOR, null, null);
    }

    private LoginUser offender(CorrectionObject o) {
        return new LoginUser(1000L + o.getId(), "obj-" + o.getId(), o.getFullName(),
                Role.OFFENDER, o.getOffice().getId(), o.getId());
    }

    private CorrectionObject byNo(String no) {
        return objectRepository.findAll().stream()
                .filter(o -> o.getCorrectionNo().equals(no)).findFirst().orElseThrow();
    }

    @Test
    void twoLevelApprovalAndLocationLinkage() {
        CorrectionObject sun = byNo("JWT26012"); // 孙满堂·龙湖·在矫·从无轨迹
        LoginUser he = offender(sun);
        LoginUser longhuStaff = staff("JGS-LH");
        Instant now = Instant.now();

        // 清掉种子里该对象可能存在的请假单，保证本用例从“无在途申请”开始（测试库可丢弃）
        leaveRepository.deleteAll(
                leaveRepository.findByOffender_IdOrderByCreatedAtDescIdDesc(sun.getId()));

        // 1) 对象提交
        LeaveApplyRequest apply = new LeaveApplyRequest("PERSONAL", "赴外地参加亲属婚礼并当日往返",
                "邻市婚宴酒店", now.minusSeconds(60), now.plusSeconds(3600));
        LeaveView v1 = leaveService.apply(apply, he);
        assertEquals(LeaveApplication.Status.PENDING_OFFICE.name(), v1.status());

        // 2) 司法所初审通过
        LeaveView v2 = leaveService.officeDecide(v1.id(),
                new LeaveDecisionRequest(true, "初审同意"), longhuStaff);
        assertEquals(LeaveApplication.Status.PENDING_BUREAU.name(), v2.status());

        // 3) 区局复核终批
        LeaveView v3 = leaveService.bureauDecide(v1.id(),
                new LeaveDecisionRequest(true, "准予外出"), supervisor());
        assertEquals(LeaveApplication.Status.APPROVED.name(), v3.status());
        assertEquals(CorrectionStatus.LEAVE,
                objectRepository.findById(sun.getId()).orElseThrow().getStatus());

        // 4) 定位联动：窗口内服务端判准假外出
        assertTrue(leaveService.isOnAuthorizedLeaveAt(sun.getId(), now.plusSeconds(600)));
        assertFalse(leaveService.isOnAuthorizedLeaveAt(sun.getId(), now.plusSeconds(7200)));

        // 5) 上报一个几何上远离龙湖活动范围的实时点（无历史锚点，不判漂移）→ 准假外出，不报越界
        TrackBatchRequest.PointDto far = new TrackBatchRequest.PointDto(
                "test-sun-leave-far-1", now.plusSeconds(2), 30.50, 114.90,
                false, 90, 4, true);
        var ingest = trackService.ingest(new TrackBatchRequest(List.of(far)), he);
        assertEquals(0, ingest.outsideFence(), "准假窗口内几何越界不应计越界数");
        assertEquals(1, ingest.leaveAuthorized(), "应记 1 个准假外出点");
        assertFalse(ingest.newViolationGenerated(), "准假期间不得一边批假一边报越界警");
        boolean noBreach = violationRepository.findTop20ByOffender_IdOrderByEventTimeDesc(sun.getId())
                .stream().noneMatch(x -> "GEOFENCE_BREACH".equals(x.getType()));
        assertTrue(noBreach, "准假外出期间不应生成越界红点");
        TrackPoint saved = trackPointRepository
                .findByOffender_IdAndResultOrderByPointTimeAscIdAsc(sun.getId(), TrackPoint.IngestResult.ACCEPTED)
                .stream().reduce((a, b) -> b).orElseThrow();
        assertTrue(saved.getLeaveAuthorized());
        assertFalse(saved.getOutsideFence());

        // 6) 销假返所 → COMPLETED，状态机回在矫
        LeaveView v4 = leaveService.returnCheckin(v1.id(), "已返所", he);
        assertEquals(LeaveApplication.Status.COMPLETED.name(), v4.status());
        assertEquals(CorrectionStatus.SERVING,
                objectRepository.findById(sun.getId()).orElseThrow().getStatus());
    }

    @Test
    void bureauDecisionCannotOverrideOtherStageOutcomes() {
        // 撞单回归：司法所已退回（RETURNED）的单，区局用旧页面快照提交复核通过，
        // 必须被拒绝且退回结论/留痕不被覆盖（此前 bureauDecide 缺环节校验会被直接盖掉）
        CorrectionObject zhou = byNo("JWT26009"); // 周文斌：司法所已退回·待修改重提
        LeaveApplication returned = leaveRepository
                .findByOffender_IdOrderByCreatedAtDescIdDesc(zhou.getId()).get(0);
        assertEquals(LeaveApplication.Status.RETURNED, returned.getStatus());

        ApiException ex = assertThrows(ApiException.class, () -> leaveService.bureauDecide(
                returned.getId(), new LeaveDecisionRequest(true, "旧页面快照提交的复核通过"), supervisor()));
        assertEquals("LEAVE_WRONG_STAGE", ex.getCode());
        LeaveApplication after = leaveRepository.findById(returned.getId()).orElseThrow();
        assertEquals(LeaveApplication.Status.RETURNED, after.getStatus(),
                "司法所的退回结论不得被区局的迟到提交覆盖");

        // 已逾期的单同样不在区局复核环节，不能借旧快照改判
        CorrectionObject wu = byNo("JWT26010"); // 吴桂芳：逾假未归（OVERDUE）
        LeaveApplication overdue = leaveRepository
                .findByOffender_IdOrderByCreatedAtDescIdDesc(wu.getId()).get(0);
        assertEquals(LeaveApplication.Status.OVERDUE, overdue.getStatus());
        ApiException ex2 = assertThrows(ApiException.class, () -> leaveService.bureauDecide(
                overdue.getId(), new LeaveDecisionRequest(false, "旧页面快照提交的复核退回"), supervisor()));
        assertEquals("LEAVE_WRONG_STAGE", ex2.getCode());
    }

    @Test
    void overdueSweepIsIdempotentAndNeverTouchesCompleted() {
        CorrectionObject yang = byNo("JWT26006"); // 杨春生：已按期销假（COMPLETED，截止于 9 天前）
        CorrectionObject wu = byNo("JWT26010");   // 吴桂芳：逾假未归（OVERDUE，违规已生成）

        assertEquals(0, countLeaveOverdue(yang.getId()), "按期销假的对象不应有逾假违规");
        assertEquals(1, countLeaveOverdue(wu.getId()), "种子数据已含 1 条逾假违规");

        // 连续巡检两轮（模拟 60s 周期反复触发）
        leaveService.sweepOverdueLeaves();
        leaveService.sweepOverdueLeaves();

        // 已按期销假的单绝不被翻回逾假，对象状态不被误升
        LeaveApplication yangLv = leaveRepository
                .findByOffender_IdOrderByCreatedAtDescIdDesc(yang.getId()).get(0);
        assertEquals(LeaveApplication.Status.COMPLETED, yangLv.getStatus());
        assertEquals(CorrectionStatus.SERVING,
                objectRepository.findById(yang.getId()).orElseThrow().getStatus());

        // 违规记录与逾假状态不重复生成
        assertEquals(0, countLeaveOverdue(yang.getId()), "已销假对象不得新增逾假违规");
        assertEquals(1, countLeaveOverdue(wu.getId()), "已逾假的单不得重复生成违规");
        LeaveApplication wuLv = leaveRepository
                .findByOffender_IdOrderByCreatedAtDescIdDesc(wu.getId()).get(0);
        assertEquals(LeaveApplication.Status.OVERDUE, wuLv.getStatus());
    }

    private long countLeaveOverdue(Long offenderId) {
        return violationRepository.findTop20ByOffender_IdOrderByEventTimeDesc(offenderId).stream()
                .filter(v -> "LEAVE_OVERDUE".equals(v.getType())).count();
    }

    @Test
    void overdueLeaveAutoEscalatesThenReturnRestoresServing() {
        CorrectionObject mait = byNo("JWT26013"); // 买买提·伊宁·在矫
        // 直接构造一张已批准且到期未销假的单子（绕过申请时段校验），并把对象置请假外出
        mait.setStatus(CorrectionStatus.LEAVE);
        objectRepository.save(mait);
        Instant now = Instant.now();
        LeaveApplication lv = new LeaveApplication(mait, "PERSONAL", "测试逾期单", "外地",
                now.minusSeconds(3 * 3600), now.minusSeconds(3600),
                LeaveApplication.Status.APPROVED, now.minusSeconds(4 * 3600));
        leaveRepository.save(lv);

        leaveService.sweepOverdueLeaves();

        LeaveApplication after = leaveRepository.findById(lv.getId()).orElseThrow();
        assertEquals(LeaveApplication.Status.OVERDUE, after.getStatus());
        assertEquals(CorrectionStatus.ADMONISHED,
                objectRepository.findById(mait.getId()).orElseThrow().getStatus(),
                "逾假未归应按状态机升训诫");
        boolean hasOverdueViolation = violationRepository
                .findTop20ByOffender_IdOrderByEventTimeDesc(mait.getId()).stream()
                .anyMatch(x -> "LEAVE_OVERDUE".equals(x.getType()));
        assertTrue(hasOverdueViolation, "应生成逾假未归违规红点");

        // 事后销假：训诫 → 在矫（合法路径），违规记录保留
        leaveService.returnCheckin(lv.getId(), "逾假后返所销假", offender(mait));
        assertEquals(CorrectionStatus.SERVING,
                objectRepository.findById(mait.getId()).orElseThrow().getStatus());
        LeaveApplication completed = leaveRepository.findById(lv.getId()).orElseThrow();
        assertEquals(LeaveApplication.Status.COMPLETED, completed.getStatus());
    }

    @Test
    void activityEnrollmentPunchRangeAndMonthlyPartialFailure() {
        CorrectionObject chen = byNo("JWT26005"); // 陈大山·青山·在矫
        CorrectionObject yang = byNo("JWT26006"); // 杨春生·青山·在矫
        // open-in-view=false：不能直接用对象上的懒加载 office 代理，按 id 取已初始化的司法所实体
        JudicialOffice qs = officeRepository.findById(chen.getOffice().getId()).orElseThrow();
        LoginUser qsStaff = staff("JGS-QS");
        Instant now = Instant.now();

        // 发布一场 40 秒后开始、当前可先报名的青山活动（半径 200m）
        ActivityPublishRequest pub = new ActivityPublishRequest(
                "青山乡测试公益劳动", "集成测试用活动，现场集合劳动两小时。", "青山乡文化广场",
                qs.getCenterLat(), qs.getCenterLng(), 200,
                now.plusSeconds(40), now.plusSeconds(3600), 30, qs.getId());
        var act = activityService.publish(pub, qsStaff);
        Long actId = act.id();

        // 未报名先打卡 → 拒绝
        ApiException notEnrolled = assertThrows(cn.sfj.jiaowutong.common.ApiException.class,
                () -> activityService.punch(actId,
                        new ActivityPunchRequest(now.plusSeconds(45), qs.getCenterLat(), qs.getCenterLng()),
                        offender(chen)));
        assertEquals("NOT_ENROLLED", notEnrolled.getCode());

        // 陈大山报名（活动开始前）并在窗内、范围内打卡（中心偏 ~33m）→ NORMAL
        activityService.enroll(actId, offender(chen));
        Map<String, Object> punchOk = activityService.punch(actId,
                new ActivityPunchRequest(now.plusSeconds(45),
                        qs.getCenterLat() + 0.0003, qs.getCenterLng()),
                offender(chen));
        assertEquals("NORMAL", punchOk.get("result"));
        assertEquals(Boolean.TRUE, punchOk.get("withinRange"));

        // 杨春生报名但在范围外打卡（偏约 1.5km）→ ABNORMAL，不拒绝、出异常红点
        activityService.enroll(actId, offender(yang));
        Map<String, Object> punchBad = activityService.punch(actId,
                new ActivityPunchRequest(now.plusSeconds(50),
                        qs.getCenterLat() + 0.012, qs.getCenterLng() + 0.009),
                offender(yang));
        assertEquals("ABNORMAL", punchBad.get("result"));
        assertEquals(Boolean.FALSE, punchBad.get("withinRange"));
        boolean hasAbnormal = violationRepository.findTop20ByOffender_IdOrderByEventTimeDesc(yang.getId())
                .stream().anyMatch(v -> "ACTIVITY_ABNORMAL".equals(v.getType()));
        assertTrue(hasAbnormal, "范围外打卡应生成异常违规红点");

        // 陈大山重复打卡 → 拒绝
        assertThrows(cn.sfj.jiaowutong.common.ApiException.class,
                () -> activityService.punch(actId,
                        new ActivityPunchRequest(now.plusSeconds(55),
                                qs.getCenterLat(), qs.getCenterLng()),
                        offender(chen)));

        // 月度批量报到（区局视角，跨所不拦）：买买提可登记（在矫），赵敏已解除/刘德海入矫登记 各失败；
        // 买买提重复勾选去重为 1 条
        CorrectionObject mait = byNo("JWT26013");
        CorrectionObject zhao = byNo("JWT26004"); // 解除（终态）
        CorrectionObject liu = byNo("JWT26007");  // 入矫登记
        String month = YearMonth.now(ZoneId.of("Asia/Shanghai")).toString();
        MonthlyReportResultView res = monthlyReportService.submit(
                List.of(mait.getId(), mait.getId(), zhao.getId(), liu.getId()), month,
                supervisor());
        assertEquals(3, res.total(), "重复勾选应去重（4 个入参 → 3 个对象）");
        assertEquals(1, res.succeeded());
        assertEquals(2, res.failed());
        assertEquals(mait.getId(), res.items().get(0).objectId());
        assertTrue(res.items().get(0).success());
        assertFalse(res.items().get(1).success());
        assertFalse(res.items().get(2).success());
        // 再次提交买买提当月报到 → 重复登记失败（逐条说明）
        MonthlyReportResultView res2 = monthlyReportService.submit(
                List.of(mait.getId()), month, supervisor());
        assertFalse(res2.items().get(0).success());
        assertTrue(res2.items().get(0).duplicated());
    }
}
