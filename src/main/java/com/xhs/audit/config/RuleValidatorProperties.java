package com.xhs.audit.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Rule validator default properties that seed the dynamic configuration store.
 */
@Data
@Component
@ConfigurationProperties(prefix = "audit.rule-validator")
public class RuleValidatorProperties {

    private int minTextLength = 25;

    private int minImageCount = 1;

    private List<String> requiredTags = new ArrayList<>(List.of(
            "东风日产",
            "尽兴由NI"));

    private List<String> carModelNames = new ArrayList<>(List.of(
            "天籁", "轩逸", "逍客", "奇骏", "X-TRAIL", "ARIYA", "艾睿雅", "N7", "N6", "NX8", "探陆",
            "NISSAN",
            "启辰大V", "启辰星", "启辰D60", "启辰", "Venucia",
            "QX50", "QX60", "Q50L", "英菲尼迪", "INFINITI"));

    private List<String> excludedTags = new ArrayList<>(List.of(
            "东风日产",
            "尽兴由NI"));
}
