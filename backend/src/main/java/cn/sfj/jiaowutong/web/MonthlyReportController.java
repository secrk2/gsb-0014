package cn.sfj.jiaowutong.web;

import cn.sfj.jiaowutong.common.ApiResult;
import cn.sfj.jiaowutong.security.CurrentUserHolder;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.service.MonthlyReportService;
import cn.sfj.jiaowutong.web.dto.MonthlyReportBatchRequest;
import cn.sfj.jiaowutong.web.vo.MonthlyReportResultView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 月度报到（当面）批量登记（管理端）：
 * - GET  /api/monthly-reports/candidates?month=yyyy-MM  取本月可勾选对象及已登记标记；
 * - POST /api/monthly-reports/batch                     一次勾选多人批量完成，逐条返回成败与原因。
 */
@RestController
@RequestMapping("/api/monthly-reports")
public class MonthlyReportController {

    private final MonthlyReportService monthlyReportService;

    public MonthlyReportController(MonthlyReportService monthlyReportService) {
        this.monthlyReportService = monthlyReportService;
    }

    @GetMapping("/candidates")
    public ApiResult<List<Map<String, Object>>> candidates(@RequestParam String month) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(monthlyReportService.candidates(month, user));
    }

    @PostMapping("/batch")
    public ApiResult<MonthlyReportResultView> batch(@Valid @RequestBody MonthlyReportBatchRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(
                monthlyReportService.submit(request.objectIds(), request.month(), user));
    }
}
