package cn.sfj.jiaowutong.security;

import cn.sfj.jiaowutong.domain.Role;

/**
 * 从 JWT 解析出的登录上下文。
 */
public record LoginUser(Long userId, String username, String realName,
                        Role role, Long officeId, Long offenderId) {
}
