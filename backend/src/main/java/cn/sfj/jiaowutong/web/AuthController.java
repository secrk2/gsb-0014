package cn.sfj.jiaowutong.web;

import cn.sfj.jiaowutong.common.ApiResult;
import cn.sfj.jiaowutong.service.AuthService;
import cn.sfj.jiaowutong.web.dto.LoginRequest;
import cn.sfj.jiaowutong.web.vo.LoginView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ApiResult<LoginView> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.success(authService.login(request.username(), request.password()));
    }

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        return ApiResult.success(Map.of("status", "UP", "app", "jiaowutong"));
    }
}
