package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 发布公益活动。时间为 UTC 瞬间；现场打卡以 (lat,lng)+radiusMeters 为核验范围。
 */
public record ActivityPublishRequest(
        @NotBlank(message = "请填写活动标题")
        @Size(max = 128)
        String title,

        @NotBlank(message = "请填写活动内容")
        @Size(min = 4, max = 1024, message = "活动内容 4-1024 字")
        String detail,

        @NotBlank(message = "请填写活动地点名称")
        @Size(max = 256)
        String address,

        @NotNull @Min(-90) @Max(90) Double lat,
        @NotNull @Min(-180) @Max(180) Double lng,

        @NotNull(message = "请填写现场打卡核验半径")
        @Min(value = 20, message = "核验半径不小于 20 米（避免 GPS 抖动全部误判异常）")
        @Max(value = 5000, message = "核验半径不大于 5000 米")
        Integer radiusMeters,

        @NotNull(message = "缺少活动开始时间") Instant startTime,
        @NotNull(message = "缺少活动结束时间") Instant endTime,

        /** 名额上限，可空表示不限 */
        @Min(value = 1, message = "名额至少 1 人") @Max(value = 9999) Integer capacity,

        /** 区局发布时指定归属司法所 id；本所干警发布时由服务端强制取本人司法所，忽略该字段 */
        Long officeId) {
}
