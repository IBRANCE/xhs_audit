package com.xhs.audit.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 自定义审核结果Repository
 * 处理复杂查询场景
 */
@Slf4j
@Repository
public class AuditResultRepositoryCustom {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 搜索审核结果（支持分页和筛选）
     * 只查询 audit_result 表，按 post_id、job_id、audit_status、时间筛选
     */
    public Page<Object[]> searchResults(String postId, String jobId, String status,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {

        StringBuilder whereClause = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();

        // postId筛选
        if (postId != null && !postId.isEmpty()) {
            whereClause.append(" AND r.post_id = ?");
            params.add(postId);
        }

        // jobId筛选
        if (jobId != null && !jobId.isEmpty()) {
            whereClause.append(" AND r.job_id = ?");
            params.add(jobId);
        }

        // 状态筛选
        if (status != null && !status.isEmpty()) {
            whereClause.append(" AND r.audit_status = ?");
            params.add(status);
        }

        // 开始时间筛选
        if (startDate != null) {
            whereClause.append(" AND r.audited_at >= ?");
            params.add(startDate);
        }

        // 结束时间筛选
        if (endDate != null) {
            whereClause.append(" AND r.audited_at <= ?");
            params.add(endDate);
        }

        // 只查询 audit_result 表的字段
        String selectSql = """
            SELECT r.post_id, r.job_id, r.url, r.audit_status, r.reasons,
                   r.confidence_score, r.model_name, r.audited_at
            FROM audit_result r
            """;

        String whereSql = " WHERE " + whereClause.toString();

        // 排序
        String orderSql = " ORDER BY r.audited_at DESC";

        // 分页
        String limitOffsetSql = " LIMIT ? OFFSET ?";

        // 构建完整SQL
        String sql = selectSql + whereSql + orderSql + limitOffsetSql;

        // 添加分页参数
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(pageable.getPageSize());
        queryParams.add(pageable.getOffset());

        // 执行查询
        List<Object[]> results = jdbcTemplate.query(sql, new AuditResultSimpleRowMapper(), queryParams.toArray());

        // 查询总数
        String countSql = "SELECT COUNT(*) FROM audit_result r WHERE " + whereClause.toString();
        long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * 按 postId 查询 xhs_content 表
     */
    public Object[] findContentByPostId(String postId) {
        String sql = """
            SELECT post_id, url, title, content, images, tags, author_id,
                   published_at, crawled_at
            FROM xhs_content
            WHERE post_id = ?
            """;

        try {
            return jdbcTemplate.queryForObject(sql, new ContentRowMapper(), postId);
        } catch (Exception e) {
            log.warn("未找到内容: postId={}", postId);
            return null;
        }
    }

    /**
     * 简化的行映射器，只处理 audit_result 表的字段
     */
    private class AuditResultSimpleRowMapper implements RowMapper<Object[]> {
        @Override
        public Object[] mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Object[]{
                    rs.getString("post_id"),
                    rs.getString("job_id"),
                    rs.getString("url"),
                    rs.getString("audit_status"),
                    parseJsonMapList(rs.getString("reasons")),
                    rs.getBigDecimal("confidence_score"),
                    rs.getString("model_name"),
                    rs.getTimestamp("audited_at") != null
                            ? rs.getTimestamp("audited_at").toLocalDateTime() : null
            };
        }
    }

    /**
     * xhs_content 表的行映射器
     */
    private class ContentRowMapper implements RowMapper<Object[]> {
        @Override
        public Object[] mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Object[]{
                    rs.getString("post_id"),
                    rs.getString("url"),
                    rs.getString("title"),
                    rs.getString("content"),
                    parseJsonStringList(rs.getString("images")),
                    parseJsonStringList(rs.getString("tags")),
                    rs.getString("author_id"),
                    rs.getTimestamp("published_at") != null
                            ? rs.getTimestamp("published_at").toLocalDateTime() : null,
                    rs.getTimestamp("crawled_at") != null
                            ? rs.getTimestamp("crawled_at").toLocalDateTime() : null
            };
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonStringList(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonMapList(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
