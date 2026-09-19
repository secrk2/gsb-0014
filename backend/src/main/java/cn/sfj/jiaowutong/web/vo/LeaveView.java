package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;
import java.util.List;

/**
 * 请假单视图。带对象脱敏信息与审批流水，供手机端（本人）与管理端（两级审批）共用。
 * 所有时刻为 UTC ISO（带 Z），前端按 timezone 换算。
 */
public record LeaveView(Long id, Long objectId, String correctionNo, String maskedName,
                        Long officeId, String officeName, String timezone,
                        String leaveType, String leaveTypeLabel,
                        String reason, String destination,
                        Instant startTime, Instant endTime,
                        String status, String statusLabel,
                        Instant submittedAt, Instant officeApprovedAt, Instant bureauApprovedAt,
                        Instant returnedAt, Instant resubmittedAt, Instant actualReturnAt,
                        Boolean overdueFlag,
                        List<LeaveEventView> events) {
}
