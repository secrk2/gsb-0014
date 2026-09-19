package cn.sfj.jiaowutong.web.dto;

import cn.sfj.jiaowutong.domain.CorrectionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionRequest(
        @NotNull(message = "目标状态不能为空") CorrectionStatus targetStatus,
        @Size(max = 256, message = "理由过长") String reason) {
}
