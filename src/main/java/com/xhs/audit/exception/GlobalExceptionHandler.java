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
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数验证异常
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
     * 文件大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        log.error("文件大小超限", ex);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("ERR_FILE_SIZE_EXCEED", "文件大小超过50MB限制"));
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("业务异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 资源未找到异常
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("资源未找到: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("ERR_INTERNAL_SERVER", "系统内部错误: " + ex.getMessage()));
    }
}
