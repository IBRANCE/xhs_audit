package com.xhs.audit.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.xhs.audit.model.dto.AuditDecision;
import com.xhs.audit.model.entity.AuditResult;

/**
 * AuditResult 与 AuditDecision 之间的转换工具
 *
 * @author XHS Audit System
 * @since 2026-01-29
 */
public final class AuditResultConverter {

    private AuditResultConverter() {
        // 工具类禁止实例化
    }

    /**
     * 将 AuditResult 转换为 AuditDecision
     */
    public static AuditDecision toDecision(AuditResult result) {
        if (result == null) {
            return null;
        }
        return AuditDecision.builder()
                .postId(result.getPostId())
                .url(result.getUrl())
                .status(result.getAuditStatus())
                .reasons(convertListToReasons(result.getReasons()))
                .confidenceScore(
                        result.getConfidenceScore() != null
                                ? result.getConfidenceScore().doubleValue()
                                : 0.0)
                .modelName(result.getModelName())
                .auditedTime(result.getAuditedAt())
                .build();
    }

    /**
     * 将 AuditDecision 转换为 List<Map>（用于存储）
     */
    public static List<Map<String, Object>> reasonsToList(List<AuditDecision.RejectReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (AuditDecision.RejectReason reason : reasons) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("dimension", reason.getDimension());
            map.put("reason", reason.getReason());
            map.put("severity", reason.getSeverity());
            list.add(map);
        }
        return list;
    }

    /**
     * 将 List<Map> 转换为 List<RejectReason>
     */
    public static List<AuditDecision.RejectReason> convertListToReasons(
            List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }

        List<AuditDecision.RejectReason> reasons = new ArrayList<>();
        for (Map<String, Object> map : list) {
            reasons.add(new AuditDecision.RejectReason(
                    (String) map.get("dimension"),
                    (String) map.get("reason"),
                    (String) map.get("severity")));
        }
        return reasons;
    }
}
