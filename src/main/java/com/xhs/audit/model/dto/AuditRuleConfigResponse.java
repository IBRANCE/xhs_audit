package com.xhs.audit.model.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.xhs.audit.model.entity.AuditRuleConfig;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuditRuleConfigResponse {

    Integer minTextLength;
    Integer minImageCount;
    List<String> requiredTags;
    List<String> carModelNames;
    List<String> excludedTags;
    LocalDateTime updatedAt;

    public static AuditRuleConfigResponse from(AuditRuleConfig config) {
        return AuditRuleConfigResponse.builder()
                .minTextLength(config.getMinTextLength())
                .minImageCount(config.getMinImageCount())
                .requiredTags(config.getRequiredTags())
                .carModelNames(config.getCarModelNames())
                .excludedTags(config.getExcludedTags())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
