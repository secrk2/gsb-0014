package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 全名查看留痕：监管员/干警查看脱敏对象全名时，必须填写理由。
 * 任何一次全名解密都落审计表，不可匿名查看。
 */
@Entity
@Table(name = "name_view_audit")
public class NameViewAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long offenderId;

    @Column(nullable = false)
    private Long viewerId;

    @Column(nullable = false, length = 64)
    private String viewerName;

    @Column(nullable = false, length = 256)
    private String reason;

    @Column(nullable = false)
    private Instant viewedAt;

    public NameViewAudit() {
    }

    public NameViewAudit(Long offenderId, Long viewerId, String viewerName, String reason) {
        this.offenderId = offenderId;
        this.viewerId = viewerId;
        this.viewerName = viewerName;
        this.reason = reason;
        this.viewedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOffenderId() { return offenderId; }
    public Long getViewerId() { return viewerId; }
    public String getViewerName() { return viewerName; }
    public String getReason() { return reason; }
    public Instant getViewedAt() { return viewedAt; }
}
