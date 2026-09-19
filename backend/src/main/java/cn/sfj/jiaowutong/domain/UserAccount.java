package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

/**
 * 登录账号：监管员 / 司法所干警 / 矫正对象三类。
 * 矫正对象账号通过 linkedOffender 关联本人档案。
 */
@Entity
@Table(name = "user_account", indexes = {
        @Index(name = "idx_user_office", columnList = "office_id")
})
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 64)
    private String passwordSalt;

    @Column(nullable = false, length = 128)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String realName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** 所属司法所；监管员（区级）为空，表示数据范围为全区 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id")
    private JudicialOffice office;

    /** 矫正对象账号关联的本人档案 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_offender_id")
    private CorrectionObject linkedOffender;

    @Column(nullable = false)
    private Boolean enabled = true;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordSalt() { return passwordSalt; }
    public String getPasswordHash() { return passwordHash; }
    public String getRealName() { return realName; }
    public Role getRole() { return role; }
    public JudicialOffice getOffice() { return office; }
    public CorrectionObject getLinkedOffender() { return linkedOffender; }
    public Boolean getEnabled() { return enabled; }

    public void setUsername(String username) { this.username = username; }
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setRealName(String realName) { this.realName = realName; }
    public void setRole(Role role) { this.role = role; }
    public void setOffice(JudicialOffice office) { this.office = office; }
    public void setLinkedOffender(CorrectionObject linkedOffender) { this.linkedOffender = linkedOffender; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
