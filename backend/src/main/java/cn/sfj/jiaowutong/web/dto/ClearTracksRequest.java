package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 清除轨迹：高危操作，二次确认必填原因并留痕 */
public record ClearTracksRequest(
        @NotBlank(message = "清除轨迹必须填写原因，该操作将留痕")
        @Size(min = 4, max = 256, message = "原因 4-256 字")
        String reason) {
}
