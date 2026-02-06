package com.xhs.audit.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条内容审核请求DTO
 * <p>
 * 用于提交单条小红书链接进行内容审核。
 * <p>
 * URL格式支持：
 * <ul>
 *   <li>标准链接：https://www.xiaohongshu.com/explore/xxx</li>
 *   <li>旧版链接：https://www.xiaohongshu.com/discovery/item/xxx</li>
 *   <li>移动端链接：https://m.xiaohongshu.com/explore/xxx</li>
 *   <li>短链接：https://xhs.com/xxx, https://xhslink.com/o/xxx</li>
 * </ul>
 *
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditRequest {

    /**
     * 小红书内容链接
     * <p>
     * 待审核的小红书笔记URL，支持多种链接格式。
     * <p>
     * 验证规则：
     * <ul>
     *   <li>不能为空（@NotBlank）</li>
     *   <li>必须符合正则表达式格式要求（@Pattern）</li>
     * </ul>
     */
    @NotBlank(message = "URL不能为空")
    @Pattern(regexp = "^https?://(www\\.|m\\.)?xiaohongshu\\.com/(explore|discovery/item)/[a-zA-Z0-9_-]+.*|^https?://xhs\\.com/[a-zA-Z0-9_-]+.*|^https?://xhslink\\.com/o/[a-zA-Z0-9]+.*", message = "URL格式不正确，必须是小红书链接")
    private String url;

    /**
     * 是否强制刷新
     * <p>
     * 设置为true时，跳过缓存，强制重新爬取和审核内容。
     * 默认值为false，表示优先使用缓存数据。
     */
    private Boolean forceRefresh = false;
}
