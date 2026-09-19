package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;

public record NameAuditView(String viewerName, String reason, Instant viewedAt) {
}
