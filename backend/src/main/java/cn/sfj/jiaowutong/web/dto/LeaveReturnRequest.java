package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.Size;

/**
 * 对象销假（返所销假登记）。note 可填返所说明。
 * 坐标可空：月度/当面到所销假时由干警在管理端代登记（另走管理端接口）；
 * 对象手机端自行销假时携带当前定位，服务端做 5 分钟时效校验。
 */
public record LeaveReturnRequest(
        @Size(max = 512)
        String note) {
}
