package com.xhs.audit.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xhs.audit.agent.ContentAuditAgent;
import com.xhs.audit.exception.BusinessException;
import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.dto.AuditDetailResponse;
import com.xhs.audit.model.dto.AuditResultItem;
import com.xhs.audit.model.entity.AuditResult;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.AuditResultRepository;
import com.xhs.audit.repository.AuditResultRepositoryCustom;
import com.xhs.audit.util.AuditResultConverter;
import com.xhs.audit.util.PostIdExtractor;

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

    @Autowired
    private AuditResultRepositoryCustom auditResultRepositoryCustom;

    /**
     * 爬取内容（流水线第一阶段）
     * 从缓存或爬虫获取内容
     *
     * @param url          小红书链接
     * @param forceRefresh 是否强制刷新（跳过缓存）
     * @return 爬取的内容
     */
    public XhsContent crawlContent(String url, boolean forceRefresh) {
        String postId = extractPostId(url);
        log.info("[爬取阶段] 开始: url={}, postId={}, forceRefresh={}", url, postId, forceRefresh);

        try {
            XhsContent content = crawlerService.crawlContent(url);
            if (content == null) {
                throw new BusinessException("ERR_CRAWL_FAILED", "内容爬取失败: 返回内容为空");
            }
            log.info("[爬取阶段] 完成: postId={}, title={}", postId, content.getTitle());
            return content;
        } catch (IllegalArgumentException e) {
            log.error("[爬取阶段] URL验证失败: {}", e.getMessage());
            throw new BusinessException("ERR_INVALID_URL", "无效的URL: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("[爬取阶段] 内容爬取失败: postId={}, error={}", postId, e.getMessage());
            throw new BusinessException("ERR_CRAWL_FAILED",
                    "内容爬取失败: " + e.getMessage() + "。请检查链接是否有效，或稍后重试", e);
        } catch (Exception e) {
            log.error("[爬取阶段] 异常: postId={}, error={}", postId, e.getMessage());
            throw new BusinessException("ERR_CRAWL_FAILED", "内容爬取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 审核内容（流水线第二阶段）
     * 对已爬取的内容进行AI审核
     *
     * @param content 已爬取的内容
     * @param jobId   任务ID（批量审核时传入，单条审核可为null）
     * @return 审核决策
     */
    public AuditDecision auditContent(XhsContent content, String jobId) {
        String postId = content.getPostId();
        String url = content.getUrl();
        log.info("[审核阶段] 开始: postId={}, url={}, jobId={}", postId, url, jobId);

        try {
            // 1. 检查是否已审核
            log.debug("[审核阶段] 检查审核结果缓存: postId={}", postId);
            Optional<AuditResult> existingResult = findLatestAuditResult(postId);
            if (existingResult.isPresent()) {
                log.info("[审核阶段] 命中审核结果缓存，跳过重复审核: postId={}", postId);
                return convertToDecision(existingResult.get());
            }

            // 2. Agent审核
            log.info("[审核阶段] 调用ContentAuditAgent进行AI审核: postId={}", postId);
            AuditDecision decision = contentAuditAgent.auditContent(content);
            log.info("[审核阶段] Agent审核完成: postId={}, status={}, confidence={}",
                    postId, decision.getStatus(), decision.getConfidenceScore());

            // 3. 保存审核结果
            log.debug("[审核阶段] 保存审核结果到数据库: postId={}, jobId={}", postId, jobId);
            saveAuditResult(decision, url, jobId);

            log.info("[审核阶段完成] postId={}, status={}, jobId={}", postId, decision.getStatus(), jobId);
            return decision;

        } catch (Exception e) {
            log.error("[审核阶段] 失败: postId={}, url={}", postId, url, e);
            throw new BusinessException("ERR_AUDIT_FAILED", "审核失败: " + e.getMessage(), e);
        }
    }

    /**
     * 审核单条小红书内容（完整流程，兼容旧接口）
     *
     * @param url          小红书链接
     * @param forceRefresh 是否强制刷新（跳过缓存）
     * @param jobId        任务ID（批量审核时传入，单条审核可为null）
     * @return 审核决策
     */
    public AuditDecision auditContent(String url, boolean forceRefresh, String jobId) {
        try {
            log.info("[审核流程] 开始: url={}, forceRefresh={}, jobId={}", url, forceRefresh, jobId);

            // 1. 爬取内容
            XhsContent content = crawlContent(url, forceRefresh);

            // 2. 审核内容
            AuditDecision decision = auditContent(content, jobId);

            log.info("[审核流程完成] postId={}, status={}, jobId={}", content.getPostId(), decision.getStatus(), jobId);
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
        try {
            return PostIdExtractor.extract(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("ERR_INVALID_URL", "无法从URL中提取postId: " + url);
        }
    }

    /**
     * 保存审核结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveAuditResult(AuditDecision decision, String url, String jobId) {
        AuditResult result = new AuditResult();
        result.setPostId(decision.getPostId());
        result.setUrl(url);
        result.setJobId(jobId); // 保存任务ID
        result.setAuditStatus(decision.getStatus());
        result.setReasons(AuditResultConverter.reasonsToList(decision.getReasons()));
        result.setConfidenceScore(
                decision.getConfidenceScore() != null
                        ? java.math.BigDecimal.valueOf(decision.getConfidenceScore())
                        : null);
        result.setModelName(decision.getModelName());
        result.setAuditedAt(LocalDateTime.now());

        auditResultRepository.save(result);
        log.info("审核结果已保存: postId={}, jobId={}", decision.getPostId(), jobId);
    }

    /**
     * 转换AuditResult为AuditDecision
     */
    private AuditDecision convertToDecision(AuditResult result) {
        return AuditResultConverter.toDecision(result);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    private Optional<AuditResult> findLatestAuditResult(String postId) {
        return auditResultRepository.findFirstByPostIdOrderByAuditedAtDesc(postId);
    }

    /**
     * 转换Reasons为List<Map>
     */
    private java.util.List<java.util.Map<String, Object>> convertReasonsToList(
            java.util.List<AuditDecision.RejectReason> reasons) {
        return AuditResultConverter.reasonsToList(reasons);
    }

    /**
     * 将List<Map>转换为List<AuditDecision.RejectReason>
     */
    private java.util.List<AuditDecision.RejectReason> convertListToReasonsForDecision(
            java.util.List<java.util.Map<String, Object>> list) {
        return AuditResultConverter.convertListToReasons(list);
    }

    /**
     * 搜索审核结果（支持分页和筛选）
     *
     * @param postId    帖子ID精确匹配
     * @param jobId     任务ID精确匹配
     * @param status    审核状态筛选
     * @param startDate 开始时间
     * @param endDate   结束时间
     * @param pageable  分页参数
     * @return 分页的审核结果列表
     */
    @Transactional(readOnly = true)
    public Page<AuditResultItem> searchAuditResults(String postId, String jobId, String status,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        log.info("搜索审核结果: postId={}, jobId={}, status={}, startDate={}, endDate={}, page={}, size={}",
                postId, jobId, status, startDate, endDate, pageable.getPageNumber(), pageable.getPageSize());

        Page<Object[]> results = auditResultRepositoryCustom.searchResults(postId, jobId, status, startDate, endDate,
                pageable);

        List<AuditResultItem> items = results.getContent().stream()
                .map(this::convertToAuditResultItem)
                .toList();

        return new PageImpl<>(items, pageable, results.getTotalElements());
    }

    /**
     * 根据 postId 查询审核详情（包含 xhs_content 和 audit_result 的数据）
     *
     * @param postId 帖子ID
     * @return 审核详情
     */
    @Transactional(readOnly = true)
    public AuditDetailResponse getAuditDetailByPostId(String postId) {
        log.info("查询审核详情: postId={}", postId);

        // 查询 audit_result
        Optional<AuditResult> resultOpt = auditResultRepository.findFirstByPostIdOrderByAuditedAtDesc(postId);
        if (resultOpt.isEmpty()) {
            throw new BusinessException("ERR_RESULT_NOT_FOUND", "审核结果不存在: " + postId);
        }
        AuditResult result = resultOpt.get();

        // 查询 xhs_content
        Object[] contentRow = auditResultRepositoryCustom.findContentByPostId(postId);

        // 构建响应
        AuditDetailResponse.AuditDetailResponseBuilder builder = AuditDetailResponse.builder();

        // audit_result 字段
        builder.postId(result.getPostId());
        builder.jobId(result.getJobId());
        builder.url(result.getUrl());
        builder.status(result.getAuditStatus());
        builder.reasons(convertListToReasons(result.getReasons()));
        builder.riskLevel(calculateRiskLevel(builder.build().getReasons()));
        builder.confidenceScore(result.getConfidenceScore() != null ? result.getConfidenceScore().doubleValue() : null);
        builder.modelName(result.getModelName());
        builder.auditedTime(result.getAuditedAt());

        // xhs_content 字段
        if (contentRow != null) {
            builder.url((String) contentRow[1]); // url
            builder.title((String) contentRow[2]);
            builder.content((String) contentRow[3]);
            builder.images((List<String>) contentRow[4]);
            builder.tags((List<String>) contentRow[5]);
            builder.authorId((String) contentRow[6]);
            builder.publishedAt((LocalDateTime) contentRow[7]);
            builder.crawledAt((LocalDateTime) contentRow[8]);
        }

        return builder.build();
    }

    /**
     * 将数据库查询结果转换为AuditResultItem
     * 查询结果列: post_id, job_id, url, audit_status, reasons,
     * confidence_score, model_name, audited_at
     */
    @SuppressWarnings("unchecked")
    private AuditResultItem convertToAuditResultItem(Object[] row) {
        AuditResultItem.AuditResultItemBuilder builder = AuditResultItem.builder();

        // post_id (String)
        builder.postId((String) row[0]);

        // job_id (String)
        builder.jobId((String) row[1]);

        // url (String)
        builder.url((String) row[2]);

        // audit_status (String)
        builder.status((String) row[3]);

        // reasons (List<Map>) - stored as JSON
        List<Map<String, Object>> reasonsList = (List<Map<String, Object>>) row[4];
        List<AuditResultItem.RejectReason> reasons = convertListToReasons(reasonsList);
        builder.reasons(reasons);

        // Calculate riskLevel from reasons
        builder.riskLevel(calculateRiskLevel(reasons));

        // confidence_score (BigDecimal -> Double)
        if (row[5] != null) {
            builder.confidenceScore(((java.math.BigDecimal) row[5]).doubleValue());
        }

        // model_name (String)
        builder.modelName((String) row[6]);

        // audited_at (LocalDateTime)
        builder.auditedTime((LocalDateTime) row[7]);

        return builder.build();
    }

    /**
     * 将List<Map>转换为List<AuditResultItem.RejectReason>
     */
    private List<AuditResultItem.RejectReason> convertListToReasons(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }

        List<AuditResultItem.RejectReason> reasons = new ArrayList<>();
        for (Map<String, Object> map : list) {
            AuditResultItem.RejectReason reason = AuditResultItem.RejectReason.builder()
                    .dimension((String) map.get("dimension"))
                    .reason((String) map.get("reason"))
                    .severity((String) map.get("severity"))
                    .build();
            reasons.add(reason);
        }
        return reasons;
    }

    /**
     * 根据驳回原因计算风险等级
     */
    private String calculateRiskLevel(List<AuditResultItem.RejectReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "LOW";
        }

        boolean hasCritical = reasons.stream()
                .anyMatch(r -> "CRITICAL".equals(r.getSeverity()));
        if (hasCritical) {
            return "CRITICAL";
        }

        boolean hasHigh = reasons.stream()
                .anyMatch(r -> "HIGH".equals(r.getSeverity()));
        if (hasHigh) {
            return "HIGH";
        }

        boolean hasMedium = reasons.stream()
                .anyMatch(r -> "MEDIUM".equals(r.getSeverity()));
        if (hasMedium) {
            return "MEDIUM";
        }

        return "LOW";
    }
}
