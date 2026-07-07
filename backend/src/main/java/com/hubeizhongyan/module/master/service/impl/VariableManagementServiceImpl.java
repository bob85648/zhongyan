/*
 * 文件说明：VariableManagementServiceImpl
 * 文件业务说明：数据类别管理服务实现，负责按工序读取数据类别列表、详情与导出结果，为数据类别管理页面
 *          提供分页查询、排序和详情展示能力。
 * 业务职责：
 * 1. 按工序分页查询数据类别。
 * 2. 统计每个数据类别对应的批次数、数据点数量与最后采集时间。
 * 3. 提供单个数据类别详情与导出能力。
 * 开发者：czd
 */
package com.hubeizhongyan.module.master.service.impl;

import com.hubeizhongyan.common.domain.PageResponse;
import com.hubeizhongyan.common.exception.BusinessException;
import com.hubeizhongyan.common.support.BusinessProcessRegistry;
import com.hubeizhongyan.common.support.BusinessProcessRegistry.BusinessProcessDefinition;
import com.hubeizhongyan.module.master.dto.VariableDetailResponse;
import com.hubeizhongyan.module.master.dto.VariableSummaryResponse;
import com.hubeizhongyan.module.master.service.VariableManagementService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class VariableManagementServiceImpl implements VariableManagementService {

    private static final DateTimeFormatter FULL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "metricOrder",
        "metricCode",
        "metricName",
        "batchCount",
        "dataPointCount",
        "lastCollectTime"
    );

    private final JdbcTemplate jdbcTemplate;
    private final BusinessProcessRegistry businessProcessRegistry;

    @Override
    public PageResponse<VariableSummaryResponse> listVariables(
        Long requestedProcessId,
        String keyword,
        long pageNo,
        long pageSize,
        String sortField,
        String sortOrder
    ) {
        businessProcessRegistry.getById(requestedProcessId);
        QueryContext queryContext = buildQueryContext(requestedProcessId, keyword, sortField, sortOrder);
        long validPageNo = Math.max(pageNo, 1);
        long validPageSize = Math.max(pageSize, 1);

        Long total = jdbcTemplate.queryForObject(
            queryContext.countSql(),
            Long.class,
            queryContext.parameters().toArray()
        );

        List<Object> queryParameters = new ArrayList<>(queryContext.parameters());
        queryParameters.add(validPageSize);
        queryParameters.add((validPageNo - 1) * validPageSize);

        List<VariableSummaryResponse> records = jdbcTemplate.query(
            queryContext.listSql(),
            (rs, rowNum) -> VariableSummaryResponse.builder()
                .metricCode(rs.getString("metric_code"))
                .metricName(rs.getString("metric_name"))
                .sourceColumnName(rs.getString("source_column_name"))
                .metricOrder(readNullableInteger(rs.getObject("metric_order")))
                .batchCount(readNullableInteger(rs.getObject("batch_count")))
                .dataPointCount(readNullableInteger(rs.getObject("data_point_count")))
                .lastCollectTime(formatTimestamp(rs.getTimestamp("last_collect_time")))
                .status("ENABLED")
                .build(),
            queryParameters.toArray()
        );

        return new PageResponse<>(records, total == null ? 0 : total, validPageNo, validPageSize);
    }

    @Override
    public byte[] exportVariables(Long requestedProcessId, String keyword, String sortField, String sortOrder) {
        businessProcessRegistry.getById(requestedProcessId);
        QueryContext queryContext = buildQueryContext(requestedProcessId, keyword, sortField, sortOrder);
        List<VariableSummaryResponse> records = jdbcTemplate.query(
            queryContext.exportSql(),
            (rs, rowNum) -> VariableSummaryResponse.builder()
                .metricCode(rs.getString("metric_code"))
                .metricName(rs.getString("metric_name"))
                .sourceColumnName(rs.getString("source_column_name"))
                .metricOrder(readNullableInteger(rs.getObject("metric_order")))
                .batchCount(readNullableInteger(rs.getObject("batch_count")))
                .dataPointCount(readNullableInteger(rs.getObject("data_point_count")))
                .lastCollectTime(formatTimestamp(rs.getTimestamp("last_collect_time")))
                .status("ENABLED")
                .build(),
            queryContext.parameters().toArray()
        );

        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("数据类别编码,数据类别名称,来源字段,排序,批次数,数据点数,最后采集时间,状态").append('\n');
        for (VariableSummaryResponse item : records) {
            builder.append(csvCell(item.getMetricCode())).append(',')
                .append(csvCell(item.getMetricName())).append(',')
                .append(csvCell(item.getSourceColumnName())).append(',')
                .append(csvCell(item.getMetricOrder())).append(',')
                .append(csvCell(item.getBatchCount())).append(',')
                .append(csvCell(item.getDataPointCount())).append(',')
                .append(csvCell(item.getLastCollectTime())).append(',')
                .append(csvCell(item.getStatus())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private QueryContext buildQueryContext(Long processId, String keyword, String sortField, String sortOrder) {
        String normalizedKeyword = normalizeKeyword(keyword);
        StringBuilder baseSqlBuilder = new StringBuilder(
            """
            SELECT m.metric_code,
                   m.metric_name,
                   m.source_column_name,
                   m.metric_order,
                   COALESCE(s.batch_count, 0) AS batch_count,
                   COALESCE(s.data_point_count, 0) AS data_point_count,
                   s.last_collect_time
            FROM dim_process_metric m
            LEFT JOIN (
                SELECT process_id,
                       metric_code,
                       COUNT(DISTINCT batch_no) AS batch_count,
                       COUNT(*) AS data_point_count,
                       MAX(process_time) AS last_collect_time
                FROM fact_process_metric_value
                GROUP BY process_id, metric_code
            ) s
              ON s.process_id = m.process_id
             AND s.metric_code = m.metric_code
            WHERE m.process_id = ?
            """
        );
        List<Object> parameters = new ArrayList<>();
        parameters.add(processId);
        if (normalizedKeyword != null) {
            baseSqlBuilder.append(" AND (m.metric_code ILIKE ? OR m.metric_name ILIKE ?) ");
            parameters.add(normalizedKeyword);
            parameters.add(normalizedKeyword);
        }

        String orderBy = buildOrderByClause(sortField, sortOrder);
        String baseSql = baseSqlBuilder.toString();
        String countSql = "SELECT COUNT(*) FROM (" + baseSql + ") variable_view";
        String exportSql = baseSql + orderBy;
        String listSql = exportSql + " LIMIT ? OFFSET ?";
        return new QueryContext(countSql, listSql, exportSql, parameters);
    }

    @Override
    public VariableDetailResponse getVariableDetail(Long requestedProcessId, String metricCode) {
        BusinessProcessDefinition definition = businessProcessRegistry.getById(requestedProcessId);
        assertMetricCode(requestedProcessId, metricCode);
        VariableDetailResponse response = jdbcTemplate.queryForObject(
            """
            SELECT m.metric_code,
                   m.metric_name,
                   m.source_column_name,
                   m.metric_order,
                   COUNT(DISTINCT f.batch_no) AS batch_count,
                   COUNT(f.id) AS data_point_count,
                   AVG(s.avg_value) AS average_value,
                   AVG(s.stddev_value) AS average_std_value,
                   MIN(s.min_value) AS min_value,
                   MAX(s.max_value) AS max_value,
                   MIN(f.process_time) AS first_collect_time,
                   MAX(f.process_time) AS last_collect_time
            FROM dim_process_metric m
            LEFT JOIN fact_process_metric_value f
              ON f.process_id = m.process_id
             AND f.metric_code = m.metric_code
            LEFT JOIN ads_batch_metric_stat s
              ON s.process_id = m.process_id
             AND s.metric_code = m.metric_code
             AND s.batch_no = f.batch_no
            WHERE m.process_id = ? AND m.metric_code = ?
            GROUP BY m.metric_code, m.metric_name, m.source_column_name, m.metric_order
            """,
            (rs, rowNum) -> VariableDetailResponse.builder()
                .metricCode(rs.getString("metric_code"))
                .metricName(rs.getString("metric_name"))
                .sourceColumnName(rs.getString("source_column_name"))
                .metricOrder(readNullableInteger(rs.getObject("metric_order")))
                .processName(definition.processName())
                .batchCount(readNullableInteger(rs.getObject("batch_count")))
                .dataPointCount(readNullableInteger(rs.getObject("data_point_count")))
                .averageValue(readNullableDouble(rs.getObject("average_value")))
                .averageStdValue(readNullableDouble(rs.getObject("average_std_value")))
                .minValue(readNullableDouble(rs.getObject("min_value")))
                .maxValue(readNullableDouble(rs.getObject("max_value")))
                .firstCollectTime(formatTimestamp(rs.getTimestamp("first_collect_time")))
                .lastCollectTime(formatTimestamp(rs.getTimestamp("last_collect_time")))
                .status("ENABLED")
                .build(),
            requestedProcessId,
            metricCode
        );
        if (response == null) {
            throw new BusinessException("未找到对应数据类别详情");
        }
        return response;
    }

    private void assertMetricCode(Long processId, String metricCode) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dim_process_metric WHERE process_id = ? AND metric_code = ?",
            Long.class,
            processId,
            metricCode
        );
        if (count == null || count == 0L) {
            throw new BusinessException("未找到当前工序下的数据类别");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return "%" + keyword.trim() + "%";
    }

    private String buildOrderByClause(String sortField, String sortOrder) {
        String normalizedField = normalizeSortField(sortField);
        String normalizedOrder = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        return switch (normalizedField) {
            case "metricCode" -> " ORDER BY m.metric_code " + normalizedOrder;
            case "metricName" -> " ORDER BY m.metric_name " + normalizedOrder + ", m.metric_code ASC";
            case "batchCount" -> " ORDER BY batch_count " + normalizedOrder + ", m.metric_code ASC";
            case "dataPointCount" -> " ORDER BY data_point_count " + normalizedOrder + ", m.metric_code ASC";
            case "lastCollectTime" -> " ORDER BY s.last_collect_time " + normalizedOrder + " NULLS LAST, m.metric_code ASC";
            default -> " ORDER BY COALESCE(m.metric_order, m.id) " + normalizedOrder + ", m.metric_code ASC";
        };
    }

    private String normalizeSortField(String sortField) {
        if (!StringUtils.hasText(sortField) || !ALLOWED_SORT_FIELDS.contains(sortField)) {
            return "metricOrder";
        }
        return sortField;
    }

    private Integer readNullableInteger(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private Double readNullableDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? "--" : timestamp.toLocalDateTime().format(FULL_TIME_FORMATTER);
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private record QueryContext(
        String countSql,
        String listSql,
        String exportSql,
        List<Object> parameters
    ) {
    }
}
