package com.xhs.audit.model.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量审核请求DTO
 * <p>
 * 用于批量提交多条URL进行审核，支持最多100条链接。
 * <p>
 * 使用场景：
 * <ul>
 *   <li>Excel文件解析后的批量URL审核</li>
 *   <li>API接口批量提交审核任务</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchAuditRequest {

    /**
     * 链接列表
     * <p>
     * 待审核的小红书链接列表，每条链接需要符合URL格式规范。
     * <p>
     * 验证规则：
     * <ul>
     *   <li>列表不能为空（@NotEmpty）</li>
     *   <li>最大数量限制为100条（@Size(max=100)）</li>
     * </ul>
     */
    @NotEmpty(message = "链接列表不能为空")
    @Size(max = 100, message = "批量审核最多支持100条链接")
    private List<String> links;
}
