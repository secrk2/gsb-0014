package cn.sfj.jiaowutong.web;

import cn.sfj.jiaowutong.common.ApiResult;
import cn.sfj.jiaowutong.security.CurrentUserHolder;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.service.MonitorService;
import cn.sfj.jiaowutong.web.dto.ClearTracksRequest;
import cn.sfj.jiaowutong.web.dto.VerifyPointRequest;
import cn.sfj.jiaowutong.web.vo.MonitorOverviewView;
import cn.sfj.jiaowutong.web.vo.TrackReplayView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 定位监控（干警/监管员）：
 * - GET  /monitor/overview            在矫对象实时总览（5 秒轮询）
 * - GET  /monitor/objects/{id}/tracks 周/月轨迹（range=WEEK|MONTH，since=UTC 增量）
 * - POST /monitor/objects/{id}/points/{pointId}/verify 越界落点二次核实留痕
 * - POST /monitor/objects/{id}/clear-tracks 清除轨迹（必填原因留痕）
 * - GET  /monitor/objects/{id}/actions 监控操作留痕
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/overview")
    public ApiResult<MonitorOverviewView> overview() {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(monitorService.overview(user));
    }

    @GetMapping("/objects/{id}/tracks")
    public ApiResult<TrackReplayView> tracks(@PathVariable Long id,
                                             @RequestParam(defaultValue = "WEEK") String range,
                                             @RequestParam(required = false) Instant since) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(monitorService.replay(id, range, since, user));
    }

    @PostMapping("/objects/{id}/points/{pointId}/verify")
    public ApiResult<Map<String, Object>> verify(@PathVariable Long id,
                                                 @PathVariable Long pointId,
                                                 @Valid @RequestBody VerifyPointRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(monitorService.verifyPoint(id, pointId, request, user));
    }

    @PostMapping("/objects/{id}/clear-tracks")
    public ApiResult<Map<String, Object>> clear(@PathVariable Long id,
                                                @Valid @RequestBody ClearTracksRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(monitorService.clearTracks(id, request, user));
    }

    @GetMapping("/objects/{id}/actions")
    public ApiResult<List<Map<String, Object>>> actions(@PathVariable Long id) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(monitorService.actionLog(id, user));
    }
}
