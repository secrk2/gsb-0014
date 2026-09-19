package cn.sfj.jiaowutong.web.vo;

import java.util.List;

/**
 * 月度报到批量登记结果：逐条说明谁成功、谁因何未登记，前端一次性展示，
 * 不让干警在“部分成功”时无从判断到底哪些人办上了。
 * 每个 item 独立成败，单条失败不影响其他人，整个批次不是全成全败。
 */
public record MonthlyReportResultView(String month, int total, int succeeded, int failed,
                                      List<Item> items) {

    public record Item(Long objectId, String correctionNo, String maskedName,
                       boolean success, String reason, boolean duplicated) {
    }
}
