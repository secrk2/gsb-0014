package cn.sfj.jiaowutong.config;

import cn.sfj.jiaowutong.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 登录、健康检查、H2 控制台放行
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/health",
                        "/h2-console/**"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 直连 7104 调试时允许跨域；经 Nginx(8104) 同源访问不受影响
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
