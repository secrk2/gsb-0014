package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 对象手机端发起 / 退回后重提请假申请。
 * 起止时刻为 UTC 瞬间（ISO 带 Z），由手机端把对象选择的当地时间换算后提交，
 * 服务端按对象所属司法所时区复核，不采信无时区的本地墙钟。
 */
public record LeaveApplyRequest(
        @NotBlank(message = "请选择请假类型")
        @Size(max = 16)
        String leaveType,

        @NotBlank(message = "请填写请假事由")
        @Size(min = 4, max = 512, message = "请假事由 4-512 字")
        String reason,

        @NotBlank(message = "请填写外出目的地")
        @Size(max = 256)
        String destination,

        @NotNull(message = "缺少假期开始时间")
        Instant startTime,

        @NotNull(message = "缺少假期截止时间")
        Instant endTime) {
}
