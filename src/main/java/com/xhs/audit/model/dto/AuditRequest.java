package com.xhs.audit.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条内容审核请求
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditRequest {

    /**
     * 小红书链接（必填）
     * 支持以下格式：
     * - https://www.xiaohongshu.com/explore/xxx
     * - https://www.xiaohongshu.com/discovery/item/xxx
     * - https://m.xiaohongshu.com/explore/xxx
     * - https://xhs.com/xxx
     * - https://xhslink.com/o/xxx (短链接)
     */
    @NotBlank(message = "URL不能为空")
    @Pattern(regexp = "^https?://(www\\.|m\\.)?xiaohongshu\\.com/(explore|discovery/item)/[a-zA-Z0-9_-]+.*|^https?://xhs\\.com/[a-zA-Z0-9_-]+.*|^https?://xhslink\\.com/o/[a-zA-Z0-9]+.*", message = "URL格式不正确，必须是小红书链接")
    private String url;

    /**
     * 是否强制刷新（跳过缓存）
     */
    private Boolean forceRefresh = false;
}
