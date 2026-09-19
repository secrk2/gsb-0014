package cn.sfj.jiaowutong.web;

import cn.sfj.jiaowutong.common.ApiResult;
import cn.sfj.jiaowutong.security.CurrentUserHolder;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.service.CheckInService;
import cn.sfj.jiaowutong.service.TrackService;
import cn.sfj.jiaowutong.web.dto.CheckInRequest;
import cn.sfj.jiaowutong.web.dto.TrackBatchRequest;
import cn.sfj.jiaowutong.web.vo.TrackIngestView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 矫正对象手机端：日常报到 + 定位轨迹上报（支持离线队列批量补传）。
 */
@RestController
@RequestMapping("/api/offender")
public class OffenderController {

    private final TrackService trackService;
    private final CheckInService checkInService;

    public OffenderController(TrackService trackService, CheckInService checkInService) {
        this.trackService = trackService;
        this.checkInService = checkInService;
    }

    /**
     * 批量上报轨迹：在线单条、离线恢复后整队列补传。
     * 服务端按 clientPointId 幂等去重，按采集时间合并，拒绝过期旧位置。
     */
    @PostMapping("/tracks")
    public ApiResult<TrackIngestView> uploadTracks(@Valid @RequestBody TrackBatchRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(trackService.ingest(request, user));
    }

    /** 日常报到（定位时间校验，禁止断网后拿缓存旧位置报到） */
    @PostMapping("/check-in")
    public ApiResult<Map<String, Object>> checkIn(@Valid @RequestBody CheckInRequest request) {
        LoginUser user = CurrentUserHolder.require();
        return ApiResult.success(checkInService.checkIn(request.fixTime(), request.lat(), request.lng(), user));
    }
}
