package cn.sfj.jiaowutong.security;

/**
 * 当前请求登录用户的 ThreadLocal 持有器，由 AuthInterceptor 在 preHandle 设置、afterCompletion 清理。
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("当前线程无登录上下文");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
