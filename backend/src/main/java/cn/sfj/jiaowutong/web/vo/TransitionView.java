package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;

public record TransitionView(String fromStatus, String fromStatusLabel,
                             String toStatus, String toStatusLabel,
                             String reason, String operatorName, Instant operatedAt) {
}
