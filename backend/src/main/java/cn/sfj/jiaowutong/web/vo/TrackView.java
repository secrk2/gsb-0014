package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;

public record TrackView(String clientPointId, Instant pointTime, Double lat, Double lng,
                        boolean offlineCaptured, Instant receivedAt, boolean outsideFence,
                        boolean forbiddenZone, boolean leaveAuthorized, String result,
                        Integer battery, Integer signal, Boolean worn) {
}
