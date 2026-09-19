package cn.sfj.jiaowutong.web.vo;

/**
 * 在矫完成度（双口径，界面必须同时写明口径，不能只报一个百分数）。
 * 两个口径对“只部分报到”的对象可能给出相反结论，故并列展示并标注：
 * - CHECKIN_DAYS 打卡天数口径：区间内实际有报到的天数 / 区间自然日数（鼓励高频、额外打卡会抬高）；
 * - KEY_REPORT 关键报到口径：规定报到日当天完成报到的次数 / 应到日报到节点数（只认节点，额外打卡不抬分）。
 * 只在非报到日频繁打卡却漏报到节点的对象：前者高、后者低；反之只踩点报到者前者低、后者高。
 */
public record CompletionView(String basisFrom, String basisTo, String timezone,
                             int actualCheckinDays, int calendarDays, double checkinDayRate,
                             int keyReportDone, int keyReportDue, double keyReportRate,
                             boolean opposite,
                             String checkinDayDefinition, String keyReportDefinition) {

    public static final String CHECKIN_DAY_DEFINITION =
            "打卡天数口径（近 30 天）= 实际有报到记录的天数 ÷ 30；非报到日的额外打卡也计入分子，打卡勤即高。";
    public static final String KEY_REPORT_DEFINITION =
            "关键报到口径（近 30 天）= 规定报到星期当天完成报到的次数 ÷ 应到日报到节点数；只认节点，额外打卡不计分。";;
}
