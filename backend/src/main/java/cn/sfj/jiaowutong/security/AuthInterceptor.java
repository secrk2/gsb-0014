package cn.sfj.jiaowutong.security;

import cn.sfj.jiaowutong.common.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：解析 Bearer Token → CurrentUserHolder。
 * 未登录/Token 失效返回 401 JSON（前端显示错误态而非空白页）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "未登录或登录已失效");
        }
        try {
            LoginUser user = jwtService.parse(auth.substring(7));
            CurrentUserHolder.set(user);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "登录令牌无效或已过期");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUserHolder.clear();
    }

    private boolean writeError(HttpServletResponse response, int status, String code, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResult.error(code, message)));
        return false;
    }
}
