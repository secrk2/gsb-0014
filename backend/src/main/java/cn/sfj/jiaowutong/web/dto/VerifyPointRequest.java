package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 越界/禁区落点二次核实：干警在轨迹上对“会越界”的落点做标记核实，必填原因留痕。
 * conclusion 由服务端约束取值，服务端会用围栏几何<b>重新计算</b>该点，不轻信客户端标记。
 */
public record VerifyPointRequest(
        @NotBlank @Pattern(regexp = "REALLY_BREACH|FALSE_ALARM|ESCORT_APPROVED",
                message = "结论只能是 REALLY_BREACH/FALSE_ALARM/ESCORT_APPROVED")
        String conclusion,
        @NotBlank(message = "核实必须填写原因/处置说明，该操作将留痕")
        @Size(min = 4, max = 256, message = "原因 4-256 字")
        String reason) {
}
