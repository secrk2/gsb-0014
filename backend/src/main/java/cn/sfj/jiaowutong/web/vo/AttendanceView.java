package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;

/**
 * 公益活动打卡/报名人员视图（管理端花名册）。
 * 打卡点 NORMAL/ABNORMAL、实测距离与核验半径一并下发，异常打卡可直接定位到人和偏离距离。
 */
public record AttendanceView(Long id, Long activityId,
                             Long objectId, String correctionNo, String maskedName,
                             String enrollmentStatus,
                             String result, Instant punchTime, Instant recordedAt,
                             Double lat, Double lng,
                             Double distanceMeters, Integer radiusMeters) {
}
