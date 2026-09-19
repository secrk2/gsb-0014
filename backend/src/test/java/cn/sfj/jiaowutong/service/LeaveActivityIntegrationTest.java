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
    @Autowired private LeaveEventRepository leaveEventRepository;
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
    void bureauCannotOverrideOfficeOrFinalConclusion() {
        CorrectionObject sun = byNo("JWT26012"); // 孙满堂·龙湖·在矫
        LoginUser he = offender(sun);
        LoginUser longhuStaff = staff("JGS-LH");
        Instant now = Instant.now();
        leaveRepository.deleteAll(
                leaveRepository.findByOffender_IdOrderByCreatedAtDescIdDesc(sun.getId()));

        LeaveView v1 = leaveService.apply(new LeaveApplyRequest("PERSONAL", "测试两级审批结论不可互相覆盖",
                "邻市", now.minusSeconds(60), now.plusSeconds(1800)), he);

        // 司法所初审通过并留下初审意见
        leaveService.officeDecide(v1.id(),
                new LeaveDecisionRequest(true, "初审意见-必须保留"), longhuStaff);

        // 区局还没轮到时，司法所不能对同一单二次初审（防并发/重复提交覆盖）
        ApiException officeAgain = assertThrows(ApiException.class,
                () -> leaveService.officeDecide(v1.id(),
                        new LeaveDecisionRequest(false, "司法所反悔想退回"), longhuStaff));
        assertEquals("LEAVE_WRONG_STAGE", officeAgain.getCode());

        // 区局正常终批
        leaveService.bureauDecide(v1.id(),
                new LeaveDecisionRequest(true, "区局意见-准予外出"), supervisor());
        assertEquals(LeaveApplication.Status.APPROVED.name(),
                leaveRepository.findById(v1.id()).orElseThrow().getStatus().name());

        // 终批之后区局再点“退回/改判”必须被拒，不能把已准假单翻成已退回
        ApiException afterFinal = assertThrows(ApiException.class,
                () -> leaveService.bureauDecide(v1.id(),
                        new LeaveDecisionRequest(false, "区局事后想退回覆盖"), supervisor()));
        assertEquals("LEAVE_WRONG_STAGE", afterFinal.getCode());

        LeaveApplication unchanged = leaveRepository.findById(v1.id()).orElseThrow();
        assertEquals(LeaveApplication.Status.APPROVED, unchanged.getStatus(),
                "后提交的越环节操作不得覆盖已终批结论");
        List<LeaveEvent> events = leaveEventRepository
                .findByLeaveIdOrderByOccurredAtAscIdAsc(v1.id());
        assertTrue(events.stream().anyMatch(e -> "OFFICE_APPROVED".equals(e.getAction())
                        && "初审意见-必须保留".equals(e.getComment())),
                "司法所初审意见必须保留，未被区局覆盖");
        assertTrue(events.stream().anyMatch(e -> "BUREAU_APPROVED".equals(e.getAction())
                && "区局意见-准予外出".equals(e.getComment())), "区局终批意见应保留");
        assertFalse(events.stream().anyMatch(e -> "BUREAU_RETURNED".equals(e.getAction())),
                "被拒的越环节退回不得产生退回流水");

        // 还原孙满堂到在矫/无在途单，避免影响同库其他用例
        leaveService.returnCheckin(v1.id(), "测试用例清理销假", he);
        assertEquals(CorrectionStatus.SERVING,
                objectRepository.findById(sun.getId()).orElseThrow().getStatus());
    }

    @Test
    void overdueSweepIsIdempotentAndNeverReopensCompletedLeave() {
        JudicialOffice lh = officeRepository.findAll().stream()
                .filter(x -> x.getCode().equals("JGS-LH")).findFirst().orElseThrow();
        Instant now = Instant.now();

        // 场景一：到期未销假 → 巡检只升一次；反复巡检不得重复登记违规/重复升状态
        CorrectionObject overdueObj = new CorrectionObject();
        overdueObj.setCorrectionNo("JWT-TEST-OVERDUE-1");
        overdueObj.setFullName("测试逾假");
        overdueObj.setMaskedName("T-OVERDUE-1");
        overdueObj.setOffice(lh);
        overdueObj.setStatus(CorrectionStatus.LEAVE);
        objectRepository.save(overdueObj);
        LeaveApplication overdueLv = new LeaveApplication(overdueObj, "PERSONAL", "测试逾假幂等", "外地",
                now.minusSeconds(3 * 3600), now.minusSeconds(3600),
                LeaveApplication.Status.APPROVED, now.minusSeconds(4 * 3600));
        leaveRepository.save(overdueLv);

        leaveService.sweepOverdueLeaves();
        leaveService.sweepOverdueLeaves();
        leaveService.sweepOverdueLeaves();

        assertEquals(LeaveApplication.Status.OVERDUE,
                leaveRepository.findById(overdueLv.getId()).orElseThrow().getStatus());
        assertEquals(CorrectionStatus.ADMONISHED,
                objectRepository.findById(overdueObj.getId()).orElseThrow().getStatus());
        long violationCount = violationRepository
                .findTop20ByOffender_IdOrderByEventTimeDesc(overdueObj.getId()).stream()
                .filter(v -> "LEAVE_OVERDUE".equals(v.getType())).count();
        assertEquals(1, violationCount, "逾假违规红点只允许生成一次，巡检重复执行不得叠加");
        long sysEventCount = leaveEventRepository
                .findByLeaveIdOrderByOccurredAtAscIdAsc(overdueLv.getId()).stream()
                .filter(e -> "SYSTEM_OVERDUE".equals(e.getAction())).count();
        assertEquals(1, sysEventCount, "系统逾假判定流水只允许一条");

        // 场景二：按期销假（COMPLETED）后，巡检不得翻案、不得补判逾假
        CorrectionObject backObj = new CorrectionObject();
        backObj.setCorrectionNo("JWT-TEST-OVERDUE-2");
        backObj.setFullName("测试按期销假");
        backObj.setMaskedName("T-OVERDUE-2");
        backObj.setOffice(lh);
        backObj.setStatus(CorrectionStatus.LEAVE);
        objectRepository.save(backObj);
        // 与真实办理过程一致：假期 [now-3h, now-60s]，对象在截止前一刻销假返所、状态回在矫
        LeaveApplication backLv = new LeaveApplication(backObj, "PERSONAL", "测试按期销假不翻案", "外地",
                now.minusSeconds(3 * 3600), now.minusSeconds(60),
                LeaveApplication.Status.APPROVED, now.minusSeconds(4 * 3600));
        leaveRepository.save(backLv);
        backLv.setStatus(LeaveApplication.Status.COMPLETED);
        backLv.setActualReturnAt(now.minusSeconds(120));
        leaveRepository.save(backLv);
        leaveEventRepository.save(new LeaveEvent(backLv.getId(), "RETURN_CHECKIN",
                LeaveApplication.Status.COMPLETED, backObj.getId(), backObj.getFullName(),
                "已按期返所销假", now.minusSeconds(120)));
        backObj.setStatus(CorrectionStatus.SERVING);
        objectRepository.save(backObj);

        // 巡检多跑几轮：截止时间已过，但对象已按期销假
        leaveService.sweepOverdueLeaves();
        leaveService.sweepOverdueLeaves();

        assertEquals(LeaveApplication.Status.COMPLETED,
                leaveRepository.findById(backLv.getId()).orElseThrow().getStatus(),
                "已按期销假的单不得被巡检翻成逾假未归");
        assertEquals(CorrectionStatus.SERVING,
                objectRepository.findById(backObj.getId()).orElseThrow().getStatus(),
                "销假恢复在矫后不得被巡检再升训诫");
        assertFalse(violationRepository
                        .findTop20ByOffender_IdOrderByEventTimeDesc(backObj.getId()).stream()
                        .anyMatch(v -> "LEAVE_OVERDUE".equals(v.getType())),
                "按期销假不得生成任何逾假违规红点");
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
