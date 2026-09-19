package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 腕表定位批量上报：每 5 秒一帧（实时），或离线恢复后整队列补传。
 * pointTime 为 UTC 瞬间（ISO-8601 带 Z/偏移），服务端不采信任何无时区的“本地时间”。
 */
public record TrackBatchRequest(
        @NotNull(message = "points 不能为空")
        @Size(min = 1, max = 200, message = "单批最多 200 个轨迹点")
        @Valid
        List<PointDto> points) {

    public record PointDto(
            @NotBlank(message = "clientPointId 缺失，无法幂等去重")
            @Size(max = 64)
            String clientPointId,
            @NotNull(message = "定位采集时间缺失，禁止用旧位置冒充")
            Instant pointTime,
            @NotNull @Min(-90) @Max(90) Double lat,
            @NotNull @Min(-180) @Max(180) Double lng,
            /** 采集时是否离线（true=断网缓存补传点，false=实时点） */
            @NotNull Boolean offlineCaptured,
            /** 腕表电量 0-100（可空，老固件不回传） */
            @Min(value = 0, message = "电量非法") @Max(value = 100, message = "电量非法") Integer battery,
            /** 信号强度 0-4（可空） */
            @Min(value = 0, message = "信号强度非法") @Max(value = 4, message = "信号强度非法") Integer signal,
            /** 是否佩戴在腕（可空） */
            Boolean worn) {
    }
}
