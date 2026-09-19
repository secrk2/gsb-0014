package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevealRequest(
        @NotBlank(message = "查看全名必须填写工作理由")
        @Size(min = 4, max = 256, message = "理由不少于 4 个字，便于审计留痕")
        String reason) {
}
