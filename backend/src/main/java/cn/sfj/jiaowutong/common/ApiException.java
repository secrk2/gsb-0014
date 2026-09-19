package cn.sfj.jiaowutong.common;

/**
 * 业务异常：携带错误码，前端据此区分“越权 / 非法状态回退 / 定位过旧”等错误态。
 */
public class ApiException extends RuntimeException {

    private final String code;

    public ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ApiException forbidden(String message) {
        return new ApiException("FORBIDDEN", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException("NOT_FOUND", message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(code, message);
    }
}
