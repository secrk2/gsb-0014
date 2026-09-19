package cn.sfj.jiaowutong.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<String, HttpStatus> STATUS_MAP = Map.of(
            "FORBIDDEN", HttpStatus.FORBIDDEN,
            "NOT_FOUND", HttpStatus.NOT_FOUND,
            "INVALID_TRANSITION", HttpStatus.CONFLICT,
            "STALE_LOCATION", HttpStatus.UNPROCESSABLE_ENTITY,
            "VALIDATION_ERROR", HttpStatus.BAD_REQUEST
    );

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResult<Void>> handleApi(ApiException ex) {
        HttpStatus http = STATUS_MAP.getOrDefault(ex.getCode(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(http).body(ApiResult.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ResponseEntity.badRequest().body(ApiResult.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResult<Void>> handleUnreadable(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ApiResult.error("VALIDATION_ERROR", "请求数据格式不正确或枚举值非法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleOther(Exception ex) {
        log.error("未处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error("INTERNAL_ERROR", "服务异常，请稍后重试"));
    }
}
