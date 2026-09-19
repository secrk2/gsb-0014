package cn.sfj.jiaowutong.web;

import cn.sfj.jiaowutong.common.ApiResult;
import cn.sfj.jiaowutong.security.CurrentUserHolder;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.service.PublicActivityService;
import cn.sfj.jiaowutong.web.dto.ActivityPublishRequest;
import cn.sfj.jiaowutong.web.dto.ActivityPunchRequest;
import cn.sfj.jiaowutong.web.vo.ActivityView;
import cn.sfj.jiaowutong.web.vo.AttendanceView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公益活动。
 *
 * <p>对象手机端（/api/offender/activities）：浏览本所已发布活动、报名/取消、现场打卡；
 * <p>管理端（/api/activities）：发布、取消、列表、花名册（报名+打卡结果，异常打卡可逐条查）。
 */
@RestController
@RequestMapping("/api")
public class PublicActivityController {

    private final PublicActivityService activityService;

    public PublicActivityController(PublicActivityService activityService) {
        this.activityService = activityService;
    }

    // ---------------- 对象手机端 ----------------

    @GetMapping("/offender/activities")
    public ApiResult<List<ActivityView>> myList() {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(activityService.listForOffender(user));
    }

    @PostMapping("/offender/activities/{id}/enroll")
    public ApiResult<ActivityView> enroll(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(activityService.enroll(id, user));
    }

    @PostMapping("/offender/activities/{id}/cancel")
    public ApiResult<ActivityView> cancelEnroll(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(activityService.cancelEnrollment(id, user));
    }

    /** 现场打卡：服务端用当前定位重算是否在活动点核验范围内，范围外记异常不丢弃 */
    @PostMapping("/offender/activities/{id}/punch")
    public ApiResult<Map<String, Object>> punch(@PathVariable Long id,
                                                @Valid @RequestBody ActivityPunchRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(activityService.punch(id, request, user));
    }

    // ---------------- 管理端 ----------------

    @GetMapping("/activities")
    public ApiResult<List<ActivityView>> list() {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(activityService.listStaff(user));
    }

    @PostMapping("/activities")
    public ApiResult<ActivityView> publish(@Valid @RequestBody ActivityPublishRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(activityService.publish(request, user));
    }

    @PostMapping("/activities/{id}/cancel")
    public ApiResult<ActivityView> cancel(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(activityService.cancel(id, user));
    }

    @GetMapping("/activities/{id}")
    public ApiResult<ActivityView> detail(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(activityService.staffDetail(id, user));
    }

    @GetMapping("/activities/{id}/roster")
    public ApiResult<List<AttendanceView>> roster(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(activityService.roster(id, user));
    }
}
