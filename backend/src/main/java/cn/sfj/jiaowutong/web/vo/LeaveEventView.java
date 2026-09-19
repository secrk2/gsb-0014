package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;

/**
 * 请假审批流水视图。occurredAt 为 UTC，前端按对象司法所时区展示。
 */
public record LeaveEventView(Long id, String action, String actionLabel,
                            String resultStatus, String resultStatusLabel,
                            Long operatorId, String operatorName,
                            String comment, Instant occurredAt) {
}
