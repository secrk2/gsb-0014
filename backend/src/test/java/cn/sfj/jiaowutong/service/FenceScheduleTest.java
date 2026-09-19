package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.domain.FenceSchedule;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 禁行时段判定：跨午夜回溯、按对象时区解释墙钟、DST 切换日（America/New_York）不误判。
 * 所有本地时刻都用显式 ZoneId 由 ZonedDateTime/Instant 构造，测试结果与 JVM 默认时区无关。
 */
class FenceScheduleTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    /** 周五、周六 22:00-次日 05:00 */
    private FenceSchedule weekendNight() {
        return new FenceSchedule(null, "FRIDAY,SATURDAY",
                LocalTime.of(22, 0), LocalTime.of(5, 0), "周末夜间禁行");
    }

    private ZonedDateTime ny(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, NY);
    }

    @Test
    void crossMidnightBindsLateNightToPreviousStartDay() {
        FenceSchedule sc = weekendNight();
        assertTrue(sc.crossesMidnight());

        // 周五 23:00：当天起始，生效
        assertNotNull(FenceService.scheduleActive(sc, ny(2026, 9, 18, 23, 0)));
        // 周六 03:00：属于“周五起始”的同一次禁行，生效
        assertNotNull(FenceService.scheduleActive(sc, ny(2026, 9, 19, 3, 0)));
        // 周日 03:00：起始日是周六，周六也在名单 → 生效
        assertNotNull(FenceService.scheduleActive(sc, ny(2026, 9, 20, 3, 0)));
        // 周六 23:00：周六在名单 → 生效
        assertNotNull(FenceService.scheduleActive(sc, ny(2026, 9, 19, 23, 0)));

        // 周一 03:00：起始日周日不在名单 → 不生效（不会误报）
        assertNull(FenceService.scheduleActive(sc, ny(2026, 9, 21, 3, 0)));
        // 周五 18:00：时段未开始
        assertNull(FenceService.scheduleActive(sc, ny(2026, 9, 18, 18, 0)));
        // 周六 06:00：已结束（[start,end)）
        assertNull(FenceService.scheduleActive(sc, ny(2026, 9, 19, 6, 0)));
    }

    @Test
    void sameDayDecisionFollowsZoneNotUtc() {
        // 周六 00:30 纽约 = 周六 04:30 UTC。若错误按 UTC 判“星期几/同一天”会算成 UTC 周六仍在名单，
        // 本例起始日应为周五；用纽约本地日历六凌晨仍属周五夜禁行 → 生效
        FenceSchedule sc = weekendNight();
        ZonedDateTime satEarlyMorningNy = ny(2026, 9, 19, 0, 30);
        assertEquals("SATURDAY", satEarlyMorningNy.getDayOfWeek().toString());
        assertNotNull(FenceService.scheduleActive(sc, satEarlyMorningNy),
                "纽约周六凌晨应回溯到周五起始时段");
    }

    @Test
    void fallBackRepeatedHourStaysConsistent() {
        // 2026-11-01 02:00 纽约回拨到 01:00（EDT→EST），01:00-02:00 经历两次。
        // 周五/周六 22:00-05:00：10-31(周六) 23:30 EDT 生效；11-01(周日) 04:30 EST 仍属周六夜，生效。
        FenceSchedule sc = weekendNight();
        assertNotNull(FenceService.scheduleActive(sc, ny(2026, 10, 31, 23, 30)),
                "回拨前周六深夜生效");
        assertNotNull(FenceService.scheduleActive(sc, ny(2026, 11, 1, 4, 30)),
                "回拨后周日凌晨仍归同一次周六禁行");
        assertNull(FenceService.scheduleActive(sc, ny(2026, 11, 1, 5, 30)),
                "05:00 后解除，不因重复小时误报");
    }

    @Test
    void springForwardGapDoesNotThrowOrFalseAlarm() {
        // 2026-03-08 02:00→03:00 纽约春进。每日 22:00-06:00：
        // 本地 02:30 不存在（被调到 03:30），03:30 < 06:00 仍在禁行窗口；本地 01:30 也在窗口。
        FenceSchedule nightly = new FenceSchedule(null, null,
                LocalTime.of(22, 0), LocalTime.of(6, 0), "夜间禁行");
        ZonedDateTime adjusted = ny(2026, 3, 8, 2, 30); // 弹簧到 03:30 EDT
        assertEquals(3, adjusted.getHour());
        assertNotNull(FenceService.scheduleActive(nightly, adjusted));
        assertNotNull(FenceService.scheduleActive(nightly, ny(2026, 3, 8, 1, 30)));
        // 当天 12:00（春进后）不在窗口
        assertNull(FenceService.scheduleActive(nightly, ny(2026, 3, 8, 12, 0)));
    }

    @Test
    void dailyScheduleWithoutDaysAppliesEveryDay() {
        FenceSchedule allDay = new FenceSchedule(null, "",
                LocalTime.of(0, 0), LocalTime.of(23, 59), "全天");
        assertNotNull(FenceService.scheduleActive(allDay, ny(2026, 9, 23, 12, 0)));
    }
}
