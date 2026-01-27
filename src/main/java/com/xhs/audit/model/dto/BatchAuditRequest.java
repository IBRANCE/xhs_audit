package com.xhs.audit.model.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量审核请求
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchAuditRequest {

    /**
     * 链接列表（最多100条）
     */
    @NotEmpty(message = "链接列表不能为空")
    @Size(max = 100, message = "批量审核最多支持100条链接")
    private List<String> links;
}
