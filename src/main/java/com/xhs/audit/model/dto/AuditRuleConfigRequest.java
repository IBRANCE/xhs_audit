package com.xhs.audit.model.dto;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuditRuleConfigRequest {

    @NotNull
    @Min(1)
    private Integer minTextLength;

    @NotNull
    @Min(0)
    private Integer minImageCount;

    @NotEmpty
    private List<String> requiredTags;

    @NotEmpty
    private List<String> carModelNames;

    @NotEmpty
    private List<String> excludedTags;
}
