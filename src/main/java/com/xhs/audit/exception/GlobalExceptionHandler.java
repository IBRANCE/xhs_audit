package com.xhs.audit.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import lombok.extern.slf4j.Slf4j;
import com.xhs.audit.model.dto.ApiResponse;

/**
 * 全局异常处理器
 * <p>
 * 统一处理应用中抛出的各类异常，返回规范的错误响应。
 * <p>
 * 异常处理映射：
 * <table border="1">
 *   <tr><th>异常类型</th><th>HTTP状态</th><th>错误码前缀</th></tr>
 *   <tr><td>MethodArgumentNotValidException</td><td>400</td><td>ERR_VALIDATION</td></tr>
 *   <tr><td>MaxUploadSizeExceededException</td><td>413</td><td>ERR_FILE_SIZE_EXCEED</td></tr>
 *   <tr><td>BusinessException</td><td>400</td><td>ERR_XXX</td></tr>
 *   <tr><td>ResourceNotFoundException</td><td>404</td><td>ERR_NOT_FOUND_XXX</td></tr>
 *   <tr><td>其他Exception</td><td>500</td><td>ERR_INTERNAL_SERVER</td></tr>
 * </table>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数校验异常
     * <p>
     * 当使用 @Valid 注解校验失败时触发
     *
     * @param ex 校验异常
     * @return 错误响应（包含所有字段错误信息）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("参数验证失败: {}", errors);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("ERR_VALIDATION", "参数验证失败", errors));
    }

    /**
     * 处理文件大小超限异常
     * <p>
     * 当上传文件超过配置的最大限制时触发
     *
     * @param ex 上传大小超限异常
     * @return 错误响应（HTTP 413 Payload Too Large）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        log.error("文件大小超限", ex);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("ERR_FILE_SIZE_EXCEED", "文件大小超过50MB限制"));
    }

    /**
     * 处理业务异常
     * <p>
     * 业务逻辑层面的异常，如参数校验、状态错误等
     *
     * @param ex 业务异常
     * @return 错误响应（HTTP 400 Bad Request）
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("业务异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理资源未找到异常
     * <p>
     * 当请求的资源不存在时触发，如查询不存在的任务ID
     *
     * @param ex 资源未找到异常
     * @return 错误响应（HTTP 404 Not Found）
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("资源未找到: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理通用异常
     * <p>
     * 兜底处理未预期的异常，记录堆栈信息便于排查
     *
     * @param ex 其他异常
     * @return 错误响应（HTTP 500 Internal Server Error）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("ERR_INTERNAL_SERVER", "系统内部错误: " + ex.getMessage()));
    }
}
