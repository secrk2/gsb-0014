package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.domain.CorrectionObject;
import cn.sfj.jiaowutong.domain.JudicialOffice;
import cn.sfj.jiaowutong.domain.LeaveApplication;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请假窗口判定：只有“准假状态（APPROVED/OVERDUE）”且定位时刻落在 [start,end] 内
 * 才允许越出活动围栏不算越界。窗口边界含起止；窗口外（假期结束未销假之后）恢复越界判定。
 */
class LeaveWindowTest {

    private LeaveApplication leave(LeaveApplication.Status status, Instant start, Instant end) {
        JudicialOffice office = new JudicialOffice("X", "测试所", "辖区", 30.0, 114.0, 1000);
        CorrectionObject obj = new CorrectionObject();
        obj.setOffice(office);
        obj.setStatus(cn.sfj.jiaowutong.domain.CorrectionStatus.LEAVE);
        return new LeaveApplication(obj, "PERSONAL", "事", "外地", start, end, status, start);
    }

    @Test
    void withinWindowIsInclusiveOnBothEnds() {
        Instant t0 = Instant.parse("2026-09-20T00:00:00Z");
        Instant t1 = Instant.parse("2026-09-21T00:00:00Z");
        List<LeaveApplication> leaves = List.of(
                leave(LeaveApplication.Status.APPROVED, t0, t1));

        assertFalse(LeaveService.withinAnyLeave(leaves, t0.minusSeconds(1)), "假期开始前不算准假外出");
        assertTrue(LeaveService.withinAnyLeave(leaves, t0), "假期起始时刻应算在窗口内（含边界）");
        assertTrue(LeaveService.withinAnyLeave(leaves, t0.plusSeconds(3600)), "假期中应算在窗口内");
        assertTrue(LeaveService.withinAnyLeave(leaves, t1), "假期截止时刻应算在窗口内（含边界）");
        assertFalse(LeaveService.withinAnyLeave(leaves, t1.plusSeconds(1)), "假期结束后越界恢复报警");
    }

    @Test
    void overdueStillCoversOnlyItsWindow() {
        Instant t0 = Instant.parse("2026-09-18T00:00:00Z");
        Instant t1 = Instant.parse("2026-09-19T00:00:00Z");
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        // 已逾期单：窗口内的历史点可解释为准假；当前（远超 endTime）不在窗口，越界照报
        List<LeaveApplication> leaves = List.of(
                leave(LeaveApplication.Status.OVERDUE, t0, t1));
        assertTrue(LeaveService.withinAnyLeave(leaves, t0.plusSeconds(60)));
        assertFalse(LeaveService.withinAnyLeave(leaves, now));
    }

    @Test
    void multipleLeavesPickCoveringWindow() {
        Instant base = Instant.parse("2026-09-20T00:00:00Z");
        List<LeaveApplication> leaves = List.of(
                leave(LeaveApplication.Status.COMPLETED, base.minusSeconds(86400), base.minusSeconds(3600)),
                leave(LeaveApplication.Status.APPROVED, base, base.plusSeconds(86400)));
        // 调用方只会传入准假状态(APPROVED/OVERDUE)的单；即便混入已完成单，base 时刻由第二张准假单覆盖
        assertTrue(LeaveService.withinAnyLeave(leaves, base.plusSeconds(10)));
    }
}
