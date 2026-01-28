package com.xhs.audit.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xhs.audit.agent.ContentAuditAgent;
import com.xhs.audit.exception.BusinessException;
import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.entity.AuditResult;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.AuditResultRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 审核业务服务
 * 整合爬虫、Agent审核、结果存储的完整流程
 * 
 * @author XHS Audit System
 * @since 2026-01-27
 */
@Slf4j
@Service
public class ContentAuditService {

    @Autowired
    private CrawlerService crawlerService;

    @Autowired
    private ContentAuditAgent contentAuditAgent;

    @Autowired
    private AuditResultRepository auditResultRepository;

    private static final Pattern POST_ID_PATTERN = Pattern.compile(
            "/(explore|discovery/item)/([a-zA-Z0-9_-]+)");
    private static final Pattern SHORT_LINK_PATTERN = Pattern.compile(
            "xhslink\\.com/o/([a-zA-Z0-9]+)");

    /**
     * 审核单条小红书内容（完整流程）
     *
     * @param url          小红书链接
     * @param forceRefresh 是否强制刷新（跳过缓存）
     * @return 审核决策
     */
    @Transactional
    public AuditDecision auditContent(String url, boolean forceRefresh) {
        try {
            log.info("[审核流程] 开始: url={}, forceRefresh={}", url, forceRefresh);

            // 1. 提取postId
            String postId = extractPostId(url);
            log.debug("[审核流程] 提取PostID: {}", postId);

            // 2. 检查是否已审核（非强制刷新时）
            if (!forceRefresh) {
                log.debug("[审核流程] 检查审核结果缓存: postId={}", postId);
                Optional<AuditResult> existingResult = auditResultRepository.findByPostId(postId);
                if (existingResult.isPresent()) {
                    log.info("[审核流程] 命中审核结果缓存，跳过重复审核: postId={}", postId);
                    return convertToDecision(existingResult.get());
                }
                log.debug("[审核流程] 未命中审核结果缓存，继续处理");
            }

            // 3. 爬取内容
            log.info("[审核流程] 调用爬虫服务获取内容: postId={}", postId);
            XhsContent content;
            try {
                content = crawlerService.crawlContent(url);
                if (content == null) {
                    throw new BusinessException("ERR_CRAWL_FAILED", "内容爬取失败: 返回内容为空");
                }
            } catch (IllegalArgumentException e) {
                // URL验证失败
                log.error("[审核流程] URL验证失败: {}", e.getMessage());
                throw new BusinessException("ERR_INVALID_URL", "无效的URL: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                // 爬取或验证失败
                log.error("[审核流程] 内容爬取失败: postId={}, error={}", postId, e.getMessage());
                throw new BusinessException("ERR_CRAWL_FAILED",
                        "内容爬取失败: " + e.getMessage() + "。请检查链接是否有效，或稍后重试", e);
            }
            log.info("[审核流程] 内容获取成功: postId={}, title={}", postId, content.getTitle());

            // 4. Agent审核
            log.info("[审核流程] 调用ContentAuditAgent进行AI审核: postId={}", postId);
            AuditDecision decision = contentAuditAgent.auditContent(content);
            log.info("[审核流程] Agent审核完成: postId={}, status={}, confidence={}",
                    postId, decision.getStatus(), decision.getConfidenceScore());

            // 5. 保存审核结果
            log.debug("[审核流程] 保存审核结果到数据库: postId={}", postId);
            saveAuditResult(decision, url);

            log.info("[审核流程完成] postId={}, status={}, 耗时统计已记录", postId, decision.getStatus());
            return decision;

        } catch (Exception e) {
            log.error("审核流程失败: url={}", url, e);
            throw new BusinessException("ERR_AUDIT_FAILED", "审核失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取postId
     */
    private String extractPostId(String url) {
        // 先尝试标准链接
        Matcher matcher = POST_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(2);
        }

        // 短链接场景
        Matcher shortMatcher = SHORT_LINK_PATTERN.matcher(url);
        if (shortMatcher.find()) {
            return "short_" + shortMatcher.group(1);
        }

        throw new BusinessException("ERR_INVALID_URL", "无法从URL中提取postId: " + url);
    }

    /**
     * 保存审核结果
     */
    private void saveAuditResult(AuditDecision decision, String url) {
        AuditResult result = new AuditResult();
        result.setPostId(decision.getPostId());
        result.setUrl(url);
        result.setAuditStatus(decision.getStatus());
        result.setReasons(convertReasonsToList(decision.getReasons()));
        result.setConfidenceScore(
                decision.getConfidenceScore() != null
                        ? java.math.BigDecimal.valueOf(decision.getConfidenceScore())
                        : null);
        result.setModelName(decision.getModelName());
        result.setAuditedAt(LocalDateTime.now());

        auditResultRepository.save(result);
        log.info("审核结果已保存: postId={}", decision.getPostId());
    }

    /**
     * 转换AuditResult为AuditDecision
     */
    private AuditDecision convertToDecision(AuditResult result) {
        return AuditDecision.builder()
                .postId(result.getPostId())
                .url(result.getUrl())
                .status(result.getAuditStatus())
                .reasons(convertListToReasons(result.getReasons()))
                .confidenceScore(result.getConfidenceScore() != null ? result.getConfidenceScore().doubleValue() : 0.0)
                .modelName(result.getModelName())
                .auditedTime(result.getAuditedAt())
                .build();
    }

    /**
     * 转换Reasons为List<Map>
     */
    private java.util.List<java.util.Map<String, Object>> convertReasonsToList(
            java.util.List<AuditDecision.RejectReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (AuditDecision.RejectReason reason : reasons) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("dimension", reason.getDimension());
            map.put("reason", reason.getReason());
            map.put("severity", reason.getSeverity());
            list.add(map);
        }
        return list;
    }

    /**
     * 从List<Map>转换为Reasons
     */
    private java.util.List<AuditDecision.RejectReason> convertListToReasons(
            java.util.List<java.util.Map<String, Object>> list) {
        if (list == null || list.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        java.util.List<AuditDecision.RejectReason> reasons = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> map : list) {
            reasons.add(new AuditDecision.RejectReason(
                    (String) map.get("dimension"),
                    (String) map.get("reason"),
                    (String) map.get("severity")));
        }
        return reasons;
    }
}
