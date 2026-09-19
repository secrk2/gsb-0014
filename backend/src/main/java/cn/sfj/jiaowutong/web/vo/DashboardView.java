package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 作战台视图：各司法所「在矫漏斗」+ 今日应报到 + 红点（越界/违规）。
 * 跨时区说明：「今日应报到」按每个对象所属司法所时区判定，条目带 localDate/timezone；
 * 红点事件时间为 UTC，展示时按对象时区换算，不使用干警/服务器时区。
 */
public record DashboardView(
        String utcToday,
        String viewerRole,
        Map<String, Long> globalFunnel,
        List<OfficeFunnel> offices,
        List<DueTodayItem> todayDue,
        List<RedDotItem> redDots,
        long redDotTotal) {

    /** 单个司法所漏斗：入矫→在矫（含请假、训诫两个在矫子态）→ 解除，收监单列 */
    public record OfficeFunnel(Long officeId, String officeName, String region, String timezone,
                               long intake, long serving, long leave, long admonished,
                               long reimprisoned, long released, long activeTotal) {
    }

    public record DueTodayItem(Long objectId, String correctionNo, String maskedName,
                               String officeName, String timezone, String localDate,
                               String reportDay,
                               boolean checkedToday, boolean overdue) {
    }

    public record RedDotItem(Long id, Long objectId, String correctionNo, String maskedName,
                             String officeName, String timezone, String type, String typeLabel,
                             String detail, Instant eventTime, boolean read) {
    }
}
