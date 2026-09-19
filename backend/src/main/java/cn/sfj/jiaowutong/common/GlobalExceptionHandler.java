package cn.sfj.jiaowutong.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
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

    /**
     * 并发撞单：两人同时处理同一张单（如司法所初审与区局复核撞在一起），
     * 后到请求被乐观/悲观锁拦下——不覆盖先到者的结论，返回 409 提示刷新后确认最新状态。
     */
    @ExceptionHandler({OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class, CannotAcquireLockException.class})
    public ResponseEntity<ApiResult<Void>> handleConcurrencyConflict(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResult.error("CONCURRENT_MODIFICATION",
                        "该单据刚被他人处理，结论已发生变化，请刷新列表确认最新状态后再操作"));
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
