package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 公益活动现场打卡。fixTime 为 GPS 采集瞬间（UTC），服务端做 5 分钟实时时效校验；
 * 是否落在活动点核验范围内完全由服务端用 (lat,lng) 与活动点几何重算，不采信客户端标记。
 */
public record ActivityPunchRequest(
        @NotNull(message = "缺少定位时间，无法核验时效") Instant fixTime,
        @NotNull @Min(-90) @Max(90) Double lat,
        @NotNull @Min(-180) @Max(180) Double lng) {
}
