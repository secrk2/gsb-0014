package cn.sfj.jiaowutong.web;

import cn.sfj.jiaowutong.common.ApiResult;
import cn.sfj.jiaowutong.security.CurrentUserHolder;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.service.LeaveService;
import cn.sfj.jiaowutong.web.dto.LeaveApplyRequest;
import cn.sfj.jiaowutong.web.dto.LeaveDecisionRequest;
import cn.sfj.jiaowutong.web.dto.LeaveReturnRequest;
import cn.sfj.jiaowutong.web.vo.LeaveView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 请销假。
 *
 * <p>对象手机端（/api/offender/leaves）：发起、退回后重提、撤回、销假、查本人单据；
 * <p>管理端（/api/leaves）：司法所初审工作台 / 区局复核工作台 / 两级审批 / 代登记当面销假。
 * 两级审批、退回重提、销假与逾期判定均在 {@link LeaveService} 内完成并全程留痕。
 */
@RestController
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    // ============================ 对象手机端 ============================

    @GetMapping("/api/offender/leaves")
    public ApiResult<List<LeaveView>> myLeaves() {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(leaveService.myLeaves(user));
    }

    @PostMapping("/api/offender/leaves")
    public ApiResult<LeaveView> apply(@Valid @RequestBody LeaveApplyRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(leaveService.apply(request, user));
    }

    @PutMapping("/api/offender/leaves/{id}/resubmit")
    public ApiResult<LeaveView> resubmit(@PathVariable Long id,
                                         @Valid @RequestBody LeaveApplyRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(leaveService.resubmit(id, request, user));
    }

    @PostMapping("/api/offender/leaves/{id}/withdraw")
    public ApiResult<LeaveView> withdraw(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(leaveService.withdraw(id, user));
    }

    @PostMapping("/api/offender/leaves/{id}/return")
    public ApiResult<LeaveView> returnCheckin(@PathVariable Long id,
                                              @Valid @RequestBody(required = false) LeaveReturnRequest request) {
        LoginUser user = CurrentUserHolder.require();
        String note = request == null ? null : request.note();
        return ApiResult.success(leaveService.returnCheckin(id, note, user));
    }

    // ============================ 管理端两级审批 ============================

    /** 审批总览（审批中 + 假期中），按数据范围裁剪 */
    @GetMapping("/api/leaves")
    public ApiResult<List<LeaveView>> list(@RequestParam(required = false) String queue) {
        LoginUser user = CurrentUserHolder.require();
        if ("OFFICE".equalsIgnoreCase(queue)) {
            return ApiResult.success(leaveService.pendingOffice(user));
        }
        if ("BUREAU".equalsIgnoreCase(queue)) {
            return ApiResult.success(leaveService.pendingBureau(user));
        }
        return ApiResult.success(leaveService.listActive(user));
    }

    @GetMapping("/api/leaves/{id}")
    public ApiResult<LeaveView> detail(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(leaveService.detail(id, user));
    }

    /** 司法所初审：approve=true 转区局复核；false 退回对象修改重提（理由必填） */
    @PostMapping("/api/leaves/{id}/office-decision")
    public ApiResult<LeaveView> officeDecision(@PathVariable Long id,
                                               @Valid @RequestBody LeaveDecisionRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(leaveService.officeDecide(id, request, user));
    }

    /** 区局复核：approve=true 终批准假；false 退回（理由必填） */
    @PostMapping("/api/leaves/{id}/bureau-decision")
    public ApiResult<LeaveView> bureauDecision(@PathVariable Long id,
                                               @Valid @RequestBody LeaveDecisionRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(leaveService.bureauDecide(id, request, user));
    }

    /** 管理端代登记当面销假（对象到所销假） */
    @PostMapping("/api/leaves/{id}/staff-return")
    public ApiResult<LeaveView> staffReturn(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) LeaveReturnRequest request) {
        LoginUser user = CurrentUserHolder.require();
        String note = request == null ? null : request.note();
        return ApiResult.success(leaveService.staffReturnCheckin(id, note, user));
    }
}
