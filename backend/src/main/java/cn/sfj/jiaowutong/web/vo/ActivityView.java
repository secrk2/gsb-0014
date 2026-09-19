package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;

/**
 * 公益活动视图。对象端与管理端共用：
 * - enrolledCount / capacity 用于名额展示与满员判断；
 * - myEnrollmentStatus / myAttendanceResult 仅对对象本人有值（ENROLLED/CANCELLED/null；NORMAL/ABNORMAL/null）；
 * - canEnroll / canPunch 由服务端按当前 UTC 与活动时间窗计算，前端只负责置灰。
 */
public record ActivityView(Long id, Long officeId, String officeName, String timezone,
                           String title, String detail, String address,
                           Double lat, Double lng, Integer radiusMeters,
                           Instant startTime, Instant endTime, Integer capacity,
                           String status,
                           long enrolledCount, boolean full,
                           String myEnrollmentStatus, String myAttendanceResult,
                           Double myDistanceMeters,
                           boolean canEnroll, boolean canCancel, boolean canPunch) {
}
