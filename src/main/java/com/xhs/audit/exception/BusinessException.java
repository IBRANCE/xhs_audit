package com.xhs.audit.exception;

import lombok.Getter;

/**
 * 业务异常
 * <p>
 * 用于表示业务逻辑层面的错误，如参数校验失败、状态不正确等。
 * <p>
 * 使用示例：
 * <pre>
 * throw new BusinessException("ERR_FILE_EMPTY", "文件不能为空");
 * throw new BusinessException("ERR_INVALID_URL", "无效的URL格式", cause);
 * </pre>
 * <p>
 * 错误码规范：
 * <ul>
 *   <li>ERR_XXX - 一般错误</li>
 *   <li>VALID_XXX - 参数校验错误</li>
 *   <li>NOT_FOUND_XXX - 资源不存在</li>
 *   <li>DUPLICATE_XXX - 重复操作</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     * <p>
     * 用于客户端识别错误类型，如：
     * <ul>
     *   <li>ERR_FILE_EMPTY - 文件为空</li>
     *   <li>ERR_INVALID_URL - URL格式无效</li>
     *   <li>ERR_DUPLICATE_SUBMIT - 重复提交</li>
     * </ul>
     */
    private final String code;

    /**
     * 创建业务异常
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 创建业务异常（带原因）
     *
     * @param code    错误码
     * @param message 错误信息
     * @param cause   异常原因
     */
    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
