package personal.cx537.flashlink.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import personal.cx537.flashlink.dto.Result;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>统一将业务异常映射为 HTTP 标准状态码：
 * 410（短链过期）、404（短链不存在）、503（系统繁忙）、400（参数校验失败）、
 * 500（未知异常）。NoResourceFoundException 静默处理，避免静态资源 404 污染日志。</p>
 *
 * @author Ethan Wu
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ShortLinkExpiredException.class)
    public ResponseEntity<Result<Void>> handleShortLinkExpiredException(ShortLinkExpiredException e) {
        log.debug("短链过期，返回410：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.GONE)
                .body(Result.fail(410, e.getMessage()));
    }

    @ExceptionHandler(ShortLinkNotFoundException.class)
    public ResponseEntity<Result<Void>> handleShortLinkNotFoundException(ShortLinkNotFoundException e) {
        log.debug("短链不存在，返回404：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Result.fail(404, e.getMessage()));
    }

    @ExceptionHandler(SystemBusyException.class)
    public ResponseEntity<Result<Void>> handleSystemBusyException(SystemBusyException e) {
        log.debug("系统繁忙，返回503：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.fail(503, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<String>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = "参数校验失败";
        }
        log.debug("参数校验失败：{}", message);
        return ResponseEntity
                .badRequest()
                .body(Result.fail(400, message));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResource() {
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常：", e);
        return Result.fail(500, e.getMessage());
    }
}