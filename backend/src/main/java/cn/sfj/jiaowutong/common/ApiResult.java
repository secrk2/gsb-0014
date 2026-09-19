package cn.sfj.jiaowutong.common;

public record ApiResult<T>(boolean ok, String code, String message, T data) {

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(true, "OK", null, data);
    }

    public static <T> ApiResult<T> error(String code, String message) {
        return new ApiResult<>(false, code, message, null);
    }
}
