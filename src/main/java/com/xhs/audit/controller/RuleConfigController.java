package com.xhs.audit.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xhs.audit.model.dto.AuditRuleConfigRequest;
import com.xhs.audit.model.dto.AuditRuleConfigResponse;
import com.xhs.audit.service.AuditRuleConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rules/config")
@Tag(name = "RuleConfig", description = "内容规则配置")
@RequiredArgsConstructor
@Validated
public class RuleConfigController {

    private final AuditRuleConfigService ruleConfigService;

    @GetMapping
    @Operation(summary = "获取内容规则配置")
    public AuditRuleConfigResponse getRuleConfig() {
        return AuditRuleConfigResponse.from(ruleConfigService.getConfig());
    }

    @PutMapping
    @Operation(summary = "更新内容规则配置")
    public AuditRuleConfigResponse updateRuleConfig(@Valid @RequestBody AuditRuleConfigRequest request) {
        return AuditRuleConfigResponse.from(ruleConfigService.updateConfig(request));
    }
}
