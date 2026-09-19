package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CheckInRequest(
        @NotNull(message = "缺少定位时间，无法核验位置时效") Instant fixTime,
        @NotNull @Min(-90) @Max(90) Double lat,
        @NotNull @Min(-180) @Max(180) Double lng) {
}
