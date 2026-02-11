package com.xhs.audit.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.xhs.audit.config.RuleValidatorProperties;
import com.xhs.audit.model.dto.AuditRuleConfigRequest;
import com.xhs.audit.model.entity.AuditRuleConfig;
import com.xhs.audit.repository.AuditRuleConfigRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * Service that manages persisted rule configuration and keeps an in-memory
 * cache for fast reads.
 */
@Service
@RequiredArgsConstructor
public class AuditRuleConfigService {

    private final AuditRuleConfigRepository repository;
    private final RuleValidatorProperties defaultProperties;

    private volatile AuditRuleConfig cachedConfig;
    private final Object lock = new Object();

    @PostConstruct
    public void init() {
        synchronized (lock) {
            this.cachedConfig = repository.findById(AuditRuleConfig.SINGLETON_ID)
                    .orElseGet(this::createDefaultConfig);
        }
    }

    public AuditRuleConfig getConfig() {
        AuditRuleConfig config = cachedConfig;
        if (config == null) {
            synchronized (lock) {
                config = cachedConfig;
                if (config == null) {
                    config = repository.findById(AuditRuleConfig.SINGLETON_ID)
                            .orElseGet(this::createDefaultConfig);
                    cachedConfig = config;
                }
            }
        }
        return config;
    }

    @Transactional
    public AuditRuleConfig updateConfig(AuditRuleConfigRequest request) {
        AuditRuleConfig config = repository.findById(AuditRuleConfig.SINGLETON_ID)
                .orElseGet(this::createDefaultConfig);

        config.setMinTextLength(request.getMinTextLength());
        config.setMinImageCount(request.getMinImageCount());
        config.setRequiredTags(sanitizeList(request.getRequiredTags()));
        config.setCarModelNames(sanitizeList(request.getCarModelNames()));
        config.setExcludedTags(sanitizeList(request.getExcludedTags()));
        config.setUpdatedAt(LocalDateTime.now());

        AuditRuleConfig saved = repository.save(config);
        cachedConfig = saved;
        return saved;
    }

    private AuditRuleConfig createDefaultConfig() {
        AuditRuleConfig config = AuditRuleConfig.builder()
                .id(AuditRuleConfig.SINGLETON_ID)
                .minTextLength(defaultProperties.getMinTextLength())
                .minImageCount(defaultProperties.getMinImageCount())
                .requiredTags(sanitizeList(defaultProperties.getRequiredTags()))
                .carModelNames(sanitizeList(defaultProperties.getCarModelNames()))
                .excludedTags(sanitizeList(defaultProperties.getExcludedTags()))
                .updatedAt(LocalDateTime.now())
                .build();
        return repository.save(config);
    }

    private List<String> sanitizeList(List<String> source) {
        if (CollectionUtils.isEmpty(source)) {
            return List.of();
        }

        LinkedHashMap<String, String> normalizedToOriginal = new LinkedHashMap<>();
        for (String value : source) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String trimmed = value.trim();
            String normalized = trimmed.replaceAll("[#\\s]+", "").toLowerCase();
            normalizedToOriginal.putIfAbsent(normalized, trimmed);
        }
        return List.copyOf(normalizedToOriginal.values());
    }
}
