package cn.sfj.jiaowutong.web.vo;

public record LoginView(String token, Long userId, String username, String realName,
                        String role, String roleLabel, Long officeId, String officeName,
                        Long offenderId) {
}
