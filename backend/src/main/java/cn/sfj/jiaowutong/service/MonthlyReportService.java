package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.CorrectionObject;
import cn.sfj.jiaowutong.domain.CorrectionStatus;
import cn.sfj.jiaowutong.repo.CheckInRepository;
import cn.sfj.jiaowutong.repo.CorrectionObjectRepository;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.vo.MonthlyReportResultView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 月度报到批量登记编排：
 * - 清单：给出数据范围内本月可登记的在矫对象及“当月是否已当面报到”标记，供一次多选；
 * - 提交：逐个走 {@link MonthlyReportEntryHandler} 的独立事务，单条成败互不影响；
 *   返回逐条结果，部分失败时明确到“谁成功、谁因何未登记”。
 */
@Service
public class MonthlyReportService {

    private final MonthlyReportEntryHandler entryHandler;
    private final CorrectionObjectRepository objectRepository;
    private final CheckInRepository checkInRepository;
    private final AccessControlService accessControl;

    public MonthlyReportService(MonthlyReportEntryHandler entryHandler,
                                CorrectionObjectRepository objectRepository,
                                CheckInRepository checkInRepository,
                                AccessControlService accessControl) {
        this.entryHandler = entryHandler;
        this.objectRepository = objectRepository;
        this.checkInRepository = checkInRepository;
        this.accessControl = accessControl;
    }

    /** 本月可登记对象（数据范围裁剪），带当月 IN_PERSON 是否已登记标记 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> candidates(String month, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        YearMonth ym = parseMonth(month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<CorrectionObject> scoped = accessControl.filterByScope(objectRepository.findAll(), user);
        List<Map<String, Object>> out = new ArrayList<>();
        for (CorrectionObject o : scoped) {
            CorrectionStatus st = o.getStatus();
            boolean terminable = st == CorrectionStatus.RELEASED || st == CorrectionStatus.REIMPRISONED;
            boolean already = checkInRepository
                    .existsByOffender_IdAndMethodAndCheckDateBetween(o.getId(), "IN_PERSON", from, to);
            out.add(Map.of(
                    "objectId", o.getId(),
                    "correctionNo", o.getCorrectionNo(),
                    "maskedName", o.getMaskedName(),
                    "officeId", o.getOffice().getId(),
                    "officeName", o.getOffice().getName(),
                    "status", st.name(),
                    "statusLabel", st.getLabel(),
                    "eligible", !terminable && st != CorrectionStatus.INTAKE,
                    "reported", already));
        }
        return out;
    }

    /**
     * 批量登记：去重入参后逐条独立处理。
     * 注意整个方法不开事务（或只开只读），每条在 REQUIRES_NEW 内提交，
     * 保证第 N 条抛错时前 N-1 条已落库、结果仍逐条返回。
     */
    public MonthlyReportResultView submit(List<Long> objectIds, String month, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        YearMonth ym = parseMonth(month);

        // 去重，保持勾选顺序
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(objectIds));
        List<MonthlyReportResultView.Item> items = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;
        for (Long id : ids) {
            MonthlyReportResultView.Item item;
            try {
                item = entryHandler.registerOne(id, ym, user);
            } catch (Exception ex) {
                // 兜底：单条出现未预期异常也不拖垮整批，作为该条失败原因返回
                item = new MonthlyReportResultView.Item(id, "—", "—",
                        false, "登记失败：" + ex.getMessage(), false);
            }
            items.add(item);
            if (item.success()) {
                succeeded++;
            } else {
                failed++;
            }
        }
        return new MonthlyReportResultView(ym.toString(), ids.size(), succeeded, failed, items);
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "报到月份格式应为 yyyy-MM（如 2026-09）");
        }
    }
}
