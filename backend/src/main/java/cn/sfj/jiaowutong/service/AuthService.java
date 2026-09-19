package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.UserAccount;
import cn.sfj.jiaowutong.repo.UserAccountRepository;
import cn.sfj.jiaowutong.security.JwtService;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.security.PasswordEncoder;
import cn.sfj.jiaowutong.web.vo.LoginView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserAccountRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginView login(String username, String rawPassword) {
        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.badRequest("BAD_CREDENTIALS", "用户名或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw ApiException.forbidden("账号已停用，请联系区司法局");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordSalt(), user.getPasswordHash())) {
            throw ApiException.badRequest("BAD_CREDENTIALS", "用户名或密码错误");
        }

        Long officeId = user.getOffice() == null ? null : user.getOffice().getId();
        String officeName = user.getOffice() == null ? null : user.getOffice().getName();
        Long offenderId = user.getLinkedOffender() == null ? null : user.getLinkedOffender().getId();

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRealName(),
                user.getRole(), officeId, offenderId);
        String token = jwtService.issue(loginUser);

        return new LoginView(token, user.getId(), user.getUsername(), user.getRealName(),
                user.getRole().name(), user.getRole().getLabel(),
                officeId, officeName, offenderId);
    }
}
