package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;
import java.util.List;

/**
 * 定位监控总览：每个在矫对象一个实时条目。
 * 心跳年龄 heartbeatAgeSec 由服务端按当前 UTC 与最近有效定位时刻计算：
 * ≤{@link #ONLINE_WITHIN_SEC} 在线；超过则离线/信号延迟，前端据此标当前状态。
 */
public record MonitorOverviewView(Instant generatedAt, int onlineWithinSec,
                                  List<Item> items) {

    public static final int ONLINE_WITHIN_SEC = 30;

    public record Item(Long objectId, String correctionNo, String maskedName,
                       Long officeId, String officeName, String timezone,
                       String status, String statusLabel,
                       Instant lastPointAt, Long heartbeatAgeSec,
                       String linkState,
                       Double lat, Double lng,
                       boolean insideRange, boolean forbidden, boolean leaveAuthorized,
                       Integer battery, Integer signal, Boolean worn,
                       long driftDiscarded24h) {
    }
}
