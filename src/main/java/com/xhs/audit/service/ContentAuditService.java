package com.xhs.audit.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xhs.audit.agent.ContentAuditAgent;
import com.xhs.audit.exception.BusinessException;
import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.entity.AuditResult;
import com.xhs.audit.model.entity.XhsContent;
import com.xhs.audit.repository.AuditResultRepository;
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

    /**
     * 爬取内容（流水线第一阶段）
     * 从缓存或爬虫获取内容
     *
     * @param url          小红书链接
     * @param forceRefresh 是否强制刷新（跳过缓存）
     * @return 爬取的内容
     */
    @Transactional
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
    @Transactional
    public AuditDecision auditContent(XhsContent content, String jobId) {
        String postId = content.getPostId();
        String url = content.getUrl();
        log.info("[审核阶段] 开始: postId={}, url={}, jobId={}", postId, url, jobId);

        try {
            // 1. 检查是否已审核
            log.debug("[审核阶段] 检查审核结果缓存: postId={}", postId);
            Optional<AuditResult> existingResult = auditResultRepository.findFirstByPostIdOrderByAuditedAtDesc(postId);
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
    @Transactional
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
    private void saveAuditResult(AuditDecision decision, String url, String jobId) {
        AuditResult result = new AuditResult();
        result.setPostId(decision.getPostId());
        result.setUrl(url);
        result.setJobId(jobId);  // 保存任务ID
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

    /**
     * 转换Reasons为List<Map>
     */
    private java.util.List<java.util.Map<String, Object>> convertReasonsToList(
            java.util.List<AuditDecision.RejectReason> reasons) {
        return AuditResultConverter.reasonsToList(reasons);
    }

    /**
     * 从List<Map>转换为Reasons
     */
    private java.util.List<AuditDecision.RejectReason> convertListToReasons(
            java.util.List<java.util.Map<String, Object>> list) {
        return AuditResultConverter.convertListToReasons(list);
    }
}
