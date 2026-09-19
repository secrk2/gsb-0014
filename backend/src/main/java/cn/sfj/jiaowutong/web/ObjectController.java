package cn.sfj.jiaowutong.web;

import cn.sfj.jiaowutong.common.ApiResult;
import cn.sfj.jiaowutong.domain.CorrectionStatus;
import cn.sfj.jiaowutong.security.CurrentUserHolder;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.service.ObjectService;
import cn.sfj.jiaowutong.web.dto.RevealRequest;
import cn.sfj.jiaowutong.web.dto.TransitionRequest;
import cn.sfj.jiaowutong.web.vo.NameAuditView;
import cn.sfj.jiaowutong.web.vo.ObjectDetailView;
import cn.sfj.jiaowutong.web.vo.ObjectView;
import cn.sfj.jiaowutong.web.vo.TrackView;
import cn.sfj.jiaowutong.web.vo.TransitionView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/objects")
public class ObjectController {

    private final ObjectService objectService;

    public ObjectController(ObjectService objectService) {
        this.objectService = objectService;
    }

    /** 档案列表：默认脱敏，支持状态/司法所过滤（监管员可切所） */
    @GetMapping
    public ApiResult<List<ObjectView>> list(@RequestParam(required = false) CorrectionStatus status,
                                            @RequestParam(required = false) Long officeId) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(objectService.list(status, officeId, user));
    }

    /** 档案详情：越权访问返回 403 错误态，不返回空白 */
    @GetMapping("/{id}")
    public ApiResult<ObjectDetailView> detail(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(objectService.detail(id, user));
    }

    /** 状态机流转（入矫/销假/训诫/收监/解除等），非法回退返回 409 与原因 */
    @PostMapping("/{id}/transition")
    public ApiResult<TransitionView> transition(@PathVariable Long id,
                                                @Valid @RequestBody TransitionRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(
                objectService.transition(id, request.targetStatus(), request.reason(), user));
    }

    /** 全名查看：二次确认 + 必填理由，服务端留痕 */
    @PostMapping("/{id}/reveal-name")
    public ApiResult<Map<String, Object>> revealName(@PathVariable Long id,
                                                     @Valid @RequestBody RevealRequest request) {
        LoginUser user = CurrentUserHolder.require();
        String fullName = objectService.revealFullName(id, request.reason(), user);
        return ApiResult.success(Map.of(
                "fullName", fullName,
                "reason", request.reason(),
                "audited", true));
    }

    /** 全名查看审计记录 */
    @GetMapping("/{id}/name-audits")
    public ApiResult<List<NameAuditView>> nameAudits(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(objectService.nameAudits(id, user));
    }

    /** 轨迹回放（仅 ACCEPTED 点，重复重放点不入轨迹） */
    @GetMapping("/{id}/tracks")
    public ApiResult<List<TrackView>> tracks(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(objectService.tracks(id, user));
    }
}
