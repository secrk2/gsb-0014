package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 两级审批动作：司法所初审 / 区局复核。
 * approve=true 通过进入下一环节；false 退回对象修改重提（comment 必填，说明退回理由并留痕）。
 */
public record LeaveDecisionRequest(
        @NotNull(message = "缺少审批结论")
        Boolean approve,

        @Size(max = 512)
        String comment) {
}
