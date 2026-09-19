package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 月度报到批量登记：干警一次勾选多个对象，服务端逐个独立处理，
 * 不因其中一人失败而回滚其他人；结果逐条返回“谁成功、谁卡在哪个原因”。
 * month 为对象司法所时区的报告月份（yyyy-MM，通常为当前月）。
 */
public record MonthlyReportBatchRequest(
        @NotNull(message = "请至少勾选一名对象")
        List<@NotNull Long> objectIds,

        @NotNull(message = "缺少报到月份")
        @Size(min = 7, max = 7)
        String month) {
}
