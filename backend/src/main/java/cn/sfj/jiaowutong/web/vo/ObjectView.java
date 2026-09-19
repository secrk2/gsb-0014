package cn.sfj.jiaowutong.web.vo;

import cn.sfj.jiaowutong.domain.CorrectionObject;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 档案列表/详情通用视图：默认不含全名（fullName 恒为 null），
 * 全名仅在二次确认留痕接口单独返回。
 * 时间字段为 UTC ISO（带 Z），前端按 timezone 换算展示。
 */
public record ObjectView(Long id, String correctionNo, String maskedName, String fullName,
                         String status, String statusLabel, Long officeId, String officeName,
                         String timezone,
                         String charge, String idCardTail, String phone,
                         LocalDate startDate, LocalDate endDate, String reportDay,
                         Instant lastLocationAt, Boolean lastInsideFence, Boolean lastForbidden,
                         Boolean lastLeaveAuthorized,
                         Double lastLat, Double lastLng,
                         Integer lastBattery, Integer lastSignal, Boolean lastWorn,
                         Double fenceCenterLat, Double fenceCenterLng, Integer fenceRadiusMeters) {

    public static ObjectView of(CorrectionObject o, boolean includeFullName) {
        return new ObjectView(
                o.getId(),
                o.getCorrectionNo(),
                o.getMaskedName(),
                includeFullName ? o.getFullName() : null,
                o.getStatus().name(),
                o.getStatus().getLabel(),
                o.getOffice().getId(),
                o.getOffice().getName(),
                o.getOffice().getTimezone(),
                o.getCharge(),
                o.getIdCardTail(),
                o.getPhone(),
                o.getStartDate(),
                o.getEndDate(),
                o.getReportDay(),
                o.getLastLocationAt(),
                o.getLastInsideFence(),
                o.getLastForbidden(),
                o.getLastLeaveAuthorized(),
                o.getLastLat(),
                o.getLastLng(),
                o.getLastBattery(),
                o.getLastSignal(),
                o.getLastWorn(),
                o.getOffice().getCenterLat(),
                o.getOffice().getCenterLng(),
                o.getOffice().getFenceRadiusMeters()
        );
    }
}
