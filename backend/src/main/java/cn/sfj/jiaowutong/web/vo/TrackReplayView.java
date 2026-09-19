package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;
import java.util.List;

/**
 * 单对象轨迹回放（周/月视图）。
 * 时间窗 windowFrom/windowTo 为 UTC；前端按 timezone 渲染。
 * points 仅含 ACCEPTED 点；首屏按抽稀上限返回，since 增量轮询时只返回新点（不抽稀）。
 * totalAccepted 为当前窗口内全部有效点数（含被抽稀掉的），用于判断“无轨迹/已清空”空态。
 * lastClear 非空表示历史轨迹曾被清除，支撑“已清空”空态（区别于“从无轨迹”）。
 */
public record TrackReplayView(Long objectId, String correctionNo, String maskedName,
                              String timezone, String range,
                              Instant windowFrom, Instant windowTo, Instant generatedAt,
                              boolean incremental,
                              List<PointView> points,
                              long totalAccepted, long driftDiscardedInWindow,
                              List<FenceView> fences,
                              ClearRecord lastClear,
                              CompletionView completion) {

    public record PointView(Long id, Instant pointTime, Double lat, Double lng,
                            boolean offlineCaptured, boolean outsideFence, boolean forbidden,
                            boolean leaveAuthorized,
                            Integer battery, Integer signal, Boolean worn,
                            boolean verified, String verifyConclusion) {
    }

    public record ClearRecord(Instant at, String operatorName, String reason, long clearedCount) {
    }
}
