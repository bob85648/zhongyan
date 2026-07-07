/*
 * 文件说明：DemoDataServiceImpl
 * 文件业务说明：分析展示数据服务实现，负责从 PostgreSQL 正式分析表中按工序读取批次、趋势、对比、
 *          质量分析等业务数据，为首页、批次分析、质量分析和导入后的结果展示提供统一数据服务。
 * 业务职责：
 * 1. 按工序读取数据类别、批次与概览统计。
 * 2. 计算批次详情、趋势曲线、批次对比和质量分析结果。
 * 3. 基于 process_id 对多工序数据进行隔离查询。
 * 开发者：czd
 */
package com.hubeizhongyan.module.demo.service.impl;

import com.hubeizhongyan.common.exception.BusinessException;
import com.hubeizhongyan.common.support.BusinessProcessRegistry;
import com.hubeizhongyan.common.support.BusinessProcessRegistry.BusinessProcessDefinition;
import com.hubeizhongyan.module.demo.dto.BatchDetailResponse;
import com.hubeizhongyan.module.demo.dto.BatchOptionResponse;
import com.hubeizhongyan.module.demo.dto.BatchStatisticResponse;
import com.hubeizhongyan.module.demo.dto.ComparisonPointResponse;
import com.hubeizhongyan.module.demo.dto.DemoOverviewResponse;
import com.hubeizhongyan.module.demo.dto.ProcessOptionResponse;
import com.hubeizhongyan.module.demo.dto.QualityAnalysisResponse;
import com.hubeizhongyan.module.demo.dto.QualityBatchResponse;
import com.hubeizhongyan.module.demo.dto.QualityLevelStatisticResponse;
import com.hubeizhongyan.module.demo.dto.TrendPointResponse;
import com.hubeizhongyan.module.demo.dto.VariableOptionResponse;
import com.hubeizhongyan.module.demo.service.DemoDataService;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DemoDataServiceImpl implements DemoDataService {

    private static final DateTimeFormatter FULL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private static final String QUALITY_LEVEL_CASE = """
        CASE
            WHEN AVG(ABS(s.stddev_value / NULLIF(ABS(s.avg_value), 0))) < 0.02 THEN 'A'
            WHEN AVG(ABS(s.stddev_value / NULLIF(ABS(s.avg_value), 0))) < 0.05 THEN 'B'
            ELSE 'C'
        END
        """;

    private static final String METRIC_RATE_SUBQUERY = """
        SELECT f.process_id AS process_id,
               f.batch_no AS batch_no,
               f.metric_code AS metric_code,
               COUNT(*) AS data_point_count,
               COUNT(*) FILTER (WHERE f.metric_value IS NULL)::numeric / NULLIF(COUNT(*), 0) AS missing_rate,
               COUNT(*) FILTER (
                   WHERE f.metric_value IS NOT NULL
                     AND ABS(f.metric_value - a.avg_value) > 3 * NULLIF(a.stddev_value, 0)
               )::numeric / NULLIF(COUNT(*), 0) AS stat_outlier_rate
        FROM fact_process_metric_value f
        JOIN ads_batch_metric_stat a
          ON a.process_id = f.process_id
         AND a.batch_no = f.batch_no
         AND a.metric_code = f.metric_code
        GROUP BY f.process_id, f.batch_no, f.metric_code
        """;

    private final JdbcTemplate jdbcTemplate;
    private final BusinessProcessRegistry businessProcessRegistry;

    @Override
    public DemoOverviewResponse getOverview() {
        Long variableCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dim_process_metric", Long.class);
        Long batchCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (SELECT DISTINCT process_id, batch_no FROM fact_process_metric_value) t", Long.class);
        Long dataPointCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fact_process_metric_value", Long.class);
        List<String> processNames = businessProcessRegistry.listAll().stream().map(BusinessProcessDefinition::processName).toList();
        return DemoOverviewResponse.builder()
            .processCount((long) processNames.size())
            .variableCount(variableCount == null ? 0L : variableCount)
            .batchCount(batchCount == null ? 0L : batchCount)
            .dataPointCount(dataPointCount == null ? 0L : dataPointCount)
            .processNames(processNames)
            .build();
    }

    @Override
    public List<ProcessOptionResponse> listProcesses() {
        return businessProcessRegistry.listAll().stream()
            .map(definition -> ProcessOptionResponse.builder()
                .id(definition.id())
                .processName(definition.processName())
                .build())
            .toList();
    }

    @Override
    public List<VariableOptionResponse> listVariables(Long processId, String batchId) {
        BusinessProcessDefinition definition = businessProcessRegistry.getById(processId);
        if (StringUtils.hasText(batchId)) {
            return jdbcTemplate.query(
                """
                SELECT f.metric_code,
                       COALESCE(MAX(m.metric_name), MAX(f.metric_name), f.metric_code) AS metric_name,
                       MIN(COALESCE(m.metric_order, m.id, 999999)) AS sort_order
                FROM fact_process_metric_value f
                LEFT JOIN dim_process_metric m
                  ON m.process_id = f.process_id
                 AND m.metric_code = f.metric_code
                WHERE f.process_id = ? AND f.batch_no = ?
                GROUP BY f.metric_code
                ORDER BY sort_order, f.metric_code
                """,
                (rs, rowNum) -> VariableOptionResponse.builder()
                    .id(rs.getString("metric_code"))
                    .variableName(rs.getString("metric_name"))
                    .unit(definition.processName())
                    .build(),
                processId,
                batchId
            );
        }
        return jdbcTemplate.query(
            """
            SELECT metric_code, metric_name
            FROM dim_process_metric
            WHERE process_id = ?
            ORDER BY COALESCE(metric_order, id), metric_code
            """,
            (rs, rowNum) -> VariableOptionResponse.builder()
                .id(rs.getString("metric_code"))
                .variableName(rs.getString("metric_name"))
                .unit(definition.processName())
                .build(),
            processId
        );
    }

    @Override
    public List<BatchOptionResponse> listBatches(Long processId) {
        businessProcessRegistry.getById(processId);
        String sql = """
            SELECT s.batch_no,
                   %s AS quality_level
            FROM ads_batch_metric_stat s
            WHERE s.process_id = ?
            GROUP BY s.batch_no
            ORDER BY MAX(s.end_time) DESC
            """.formatted(QUALITY_LEVEL_CASE);
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> BatchOptionResponse.builder()
                .id(rs.getString("batch_no"))
                .batchCode(rs.getString("batch_no"))
                .qualityLevel(rs.getString("quality_level"))
                .build(),
            processId
        );
    }

    @Override
    public BatchDetailResponse getBatchDetail(Long processId, String batchId) {
        BusinessProcessDefinition definition = businessProcessRegistry.getById(processId);
        String sql = """
            SELECT s.batch_no,
                   MIN(s.start_time) AS start_time,
                   MAX(s.end_time) AS end_time,
                   COUNT(f.id) AS data_point_count,
                   COUNT(DISTINCT s.metric_code) AS variable_count,
                   COALESCE(SUM(CASE WHEN f.metric_value IS NULL THEN 1 ELSE 0 END), 0) AS missing_count,
                   COALESCE(SUM(
                       CASE
                           WHEN f.metric_value IS NOT NULL AND ABS(f.metric_value - s.avg_value) > 3 * NULLIF(s.stddev_value, 0)
                           THEN 1
                           ELSE 0
                       END
                   ), 0) AS stat_outlier_count,
                   0 AS physical_outlier_count,
                   %s AS quality_level
            FROM ads_batch_metric_stat s
            LEFT JOIN fact_process_metric_value f
              ON f.process_id = s.process_id
             AND f.batch_no = s.batch_no
             AND f.metric_code = s.metric_code
            WHERE s.batch_no = ? AND s.process_id = ?
            GROUP BY s.batch_no
            """.formatted(QUALITY_LEVEL_CASE);
        BatchDetailResponse response = jdbcTemplate.queryForObject(
            sql,
            (rs, rowNum) -> BatchDetailResponse.builder()
                .batchId(rs.getString("batch_no"))
                .batchCode(rs.getString("batch_no"))
                .processName(definition.processName())
                .startTime(formatFullTimestamp(rs.getTimestamp("start_time")))
                .endTime(formatFullTimestamp(rs.getTimestamp("end_time")))
                .qualityLevel(rs.getString("quality_level"))
                .dataPointCount(rs.getInt("data_point_count"))
                .variableCount(rs.getInt("variable_count"))
                .missingCount(rs.getInt("missing_count"))
                .statOutlierCount(rs.getInt("stat_outlier_count"))
                .physicalOutlierCount(rs.getInt("physical_outlier_count"))
                .build(),
            batchId,
            processId
        );
        if (response == null) {
            throw new BusinessException("未找到对应批次明细");
        }
        return response;
    }

    @Override
    public List<TrendPointResponse> getTrend(Long processId, String batchId, String variableId) {
        businessProcessRegistry.getById(processId);
        assertMetricCode(processId, variableId);
        return jdbcTemplate.query(
            """
            SELECT f.process_time,
                   f.metric_value,
                   s.avg_value,
                   s.stddev_value,
                   f.metric_value IS NULL AS is_missing,
                   CASE
                       WHEN f.metric_value IS NULL OR s.stddev_value IS NULL OR s.stddev_value = 0 THEN FALSE
                       WHEN ABS(f.metric_value - s.avg_value) > 3 * s.stddev_value THEN TRUE
                       ELSE FALSE
                   END AS is_stat_outlier
            FROM fact_process_metric_value f
            JOIN ads_batch_metric_stat s
              ON s.process_id = f.process_id
             AND s.batch_no = f.batch_no
             AND s.metric_code = f.metric_code
            WHERE f.batch_no = ? AND f.metric_code = ? AND f.process_id = ?
            ORDER BY f.process_time
            """,
            (rs, rowNum) -> {
                Double rawValue = readNullableDouble(rs.getObject("metric_value"));
                Double avgValue = readNullableDouble(rs.getObject("avg_value"));
                Double stdValue = readNullableDouble(rs.getObject("stddev_value"));
                Double standardValue = rawValue == null || avgValue == null || stdValue == null || stdValue == 0
                    ? null
                    : round((rawValue - avgValue) / stdValue);
                return TrendPointResponse.builder()
                    .collectTime(formatTimestamp(rs.getTimestamp("process_time")))
                    .rawValue(rawValue)
                    .cleanValue(rawValue)
                    .standardValue(standardValue)
                    .missing(rs.getBoolean("is_missing"))
                    .statOutlier(rs.getBoolean("is_stat_outlier"))
                    .physicalOutlier(false)
                    .build();
            },
            batchId,
            variableId,
            processId
        );
    }

    @Override
    public List<ComparisonPointResponse> getComparison(Long processId, String variableId) {
        businessProcessRegistry.getById(processId);
        assertMetricCode(processId, variableId);
        String sql = """
            SELECT s.batch_no,
                   s.avg_value,
                   s.stddev_value,
                   COALESCE(m.missing_rate, 0) AS missing_rate
            FROM ads_batch_metric_stat s
            LEFT JOIN (
                %s
            ) m
              ON m.process_id = s.process_id
             AND m.batch_no = s.batch_no
             AND m.metric_code = s.metric_code
            WHERE s.metric_code = ? AND s.process_id = ?
            ORDER BY s.end_time
            """.formatted(METRIC_RATE_SUBQUERY);
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> ComparisonPointResponse.builder()
                .batchCode(rs.getString("batch_no"))
                .meanValue(rs.getDouble("avg_value"))
                .stdValue(rs.getDouble("stddev_value"))
                .missingRate(rs.getDouble("missing_rate"))
                .build(),
            variableId,
            processId
        );
    }

    @Override
    public List<BatchStatisticResponse> getBatchStatistics(Long processId) {
        businessProcessRegistry.getById(processId);
        String sql = """
            SELECT s.batch_no,
                   s.metric_name,
                   s.avg_value,
                   s.stddev_value,
                   s.min_value,
                   s.max_value,
                   COALESCE(m.missing_rate, 0) AS missing_rate,
                   COALESCE(m.stat_outlier_rate, 0) AS stat_outlier_rate,
                   0 AS physical_outlier_rate
            FROM ads_batch_metric_stat s
            LEFT JOIN (
                %s
            ) m
              ON m.process_id = s.process_id
             AND m.batch_no = s.batch_no
             AND m.metric_code = s.metric_code
            WHERE s.process_id = ?
            ORDER BY s.end_time DESC, s.metric_code
            """.formatted(METRIC_RATE_SUBQUERY);
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> BatchStatisticResponse.builder()
                .batchCode(rs.getString("batch_no"))
                .variableName(rs.getString("metric_name"))
                .meanValue(rs.getDouble("avg_value"))
                .stdValue(rs.getDouble("stddev_value"))
                .minValue(rs.getDouble("min_value"))
                .maxValue(rs.getDouble("max_value"))
                .missingRate(rs.getDouble("missing_rate"))
                .statOutlierRate(rs.getDouble("stat_outlier_rate"))
                .physicalOutlierRate(rs.getDouble("physical_outlier_rate"))
                .build(),
            processId
        );
    }

    @Override
    public QualityAnalysisResponse getQualityAnalysis(Long processId, String variableId) {
        BusinessProcessDefinition definition = businessProcessRegistry.getById(processId);
        assertMetricCode(processId, variableId);
        String summarySql = """
            SELECT COUNT(*) AS total_batch_count,
                   COALESCE(SUM(COALESCE(m.data_point_count, 0)), 0) AS total_data_point_count,
                   COALESCE(AVG(s.avg_value), 0) AS avg_mean_value,
                   COALESCE(AVG(s.stddev_value), 0) AS avg_std_value,
                   COALESCE(AVG(COALESCE(m.missing_rate, 0)), 0) AS avg_missing_rate,
                   COALESCE(AVG(COALESCE(m.stat_outlier_rate, 0)), 0) AS avg_stat_outlier_rate,
                   0 AS avg_physical_outlier_rate
            FROM ads_batch_metric_stat s
            LEFT JOIN (
                %s
            ) m
              ON m.process_id = s.process_id
             AND m.batch_no = s.batch_no
             AND m.metric_code = s.metric_code
            WHERE s.metric_code = ? AND s.process_id = ?
            """.formatted(METRIC_RATE_SUBQUERY);
        SummaryMetrics summaryMetrics = jdbcTemplate.queryForObject(
            summarySql,
            (rs, rowNum) -> new SummaryMetrics(
                rs.getInt("total_batch_count"),
                rs.getInt("total_data_point_count"),
                rs.getDouble("avg_mean_value"),
                rs.getDouble("avg_std_value"),
                rs.getDouble("avg_missing_rate"),
                rs.getDouble("avg_stat_outlier_rate"),
                rs.getDouble("avg_physical_outlier_rate")
            ),
            variableId,
            processId
        );
        if (summaryMetrics == null || summaryMetrics.totalBatchCount() == 0) {
            throw new BusinessException("当前工序下暂无对应质量分析数据");
        }

        String levelSql = """
            WITH batch_level AS (
                SELECT s.batch_no,
                       %s AS quality_level,
                       AVG(s.avg_value) AS avg_value,
                       AVG(s.stddev_value) AS stddev_value,
                       AVG(COALESCE(m.missing_rate, 0)) AS missing_rate,
                       AVG(COALESCE(m.stat_outlier_rate, 0)) AS stat_outlier_rate,
                       0 AS physical_outlier_rate
                FROM ads_batch_metric_stat s
                LEFT JOIN (
                    %s
                ) m
                  ON m.process_id = s.process_id
                 AND m.batch_no = s.batch_no
                 AND m.metric_code = s.metric_code
                WHERE s.metric_code = ? AND s.process_id = ?
                GROUP BY s.batch_no
            )
            SELECT quality_level,
                   COUNT(*) AS batch_count,
                   AVG(avg_value) AS avg_mean_value,
                   AVG(stddev_value) AS avg_std_value,
                   AVG(missing_rate) AS avg_missing_rate,
                   AVG(stat_outlier_rate) AS avg_stat_outlier_rate,
                   AVG(physical_outlier_rate) AS avg_physical_outlier_rate
            FROM batch_level
            GROUP BY quality_level
            ORDER BY quality_level
            """.formatted(QUALITY_LEVEL_CASE, METRIC_RATE_SUBQUERY);
        List<QualityLevelStatisticResponse> qualityLevelStatistics = jdbcTemplate.query(
            levelSql,
            (rs, rowNum) -> QualityLevelStatisticResponse.builder()
                .qualityLevel(rs.getString("quality_level"))
                .batchCount(rs.getInt("batch_count"))
                .avgMeanValue(rs.getDouble("avg_mean_value"))
                .avgStdValue(rs.getDouble("avg_std_value"))
                .avgMissingRate(rs.getDouble("avg_missing_rate"))
                .avgStatOutlierRate(rs.getDouble("avg_stat_outlier_rate"))
                .avgPhysicalOutlierRate(rs.getDouble("avg_physical_outlier_rate"))
                .build(),
            variableId,
            processId
        );

        String batchSql = """
            SELECT s.batch_no,
                   %s AS quality_level,
                   AVG(s.avg_value) AS avg_value,
                   AVG(s.stddev_value) AS stddev_value,
                   AVG(COALESCE(m.missing_rate, 0)) AS missing_rate,
                   AVG(COALESCE(m.stat_outlier_rate, 0)) AS stat_outlier_rate,
                   0 AS physical_outlier_rate
            FROM ads_batch_metric_stat s
            LEFT JOIN (
                %s
            ) m
              ON m.process_id = s.process_id
             AND m.batch_no = s.batch_no
             AND m.metric_code = s.metric_code
            WHERE s.metric_code = ? AND s.process_id = ?
            GROUP BY s.batch_no
            ORDER BY MAX(s.end_time) DESC
            """.formatted(QUALITY_LEVEL_CASE, METRIC_RATE_SUBQUERY);
        List<QualityBatchResponse> batchStatistics = jdbcTemplate.query(
            batchSql,
            (rs, rowNum) -> QualityBatchResponse.builder()
                .batchId(rs.getString("batch_no"))
                .batchCode(rs.getString("batch_no"))
                .qualityLevel(rs.getString("quality_level"))
                .meanValue(rs.getDouble("avg_value"))
                .stdValue(rs.getDouble("stddev_value"))
                .missingRate(rs.getDouble("missing_rate"))
                .statOutlierRate(rs.getDouble("stat_outlier_rate"))
                .physicalOutlierRate(rs.getDouble("physical_outlier_rate"))
                .build(),
            variableId,
            processId
        );

        return QualityAnalysisResponse.builder()
            .processId(processId)
            .processName(definition.processName())
            .variableId(variableId)
            .variableName(queryMetricName(processId, variableId))
            .unit("--")
            .totalBatchCount(summaryMetrics.totalBatchCount())
            .totalDataPointCount(summaryMetrics.totalDataPointCount())
            .avgMeanValue(summaryMetrics.avgMeanValue())
            .avgStdValue(summaryMetrics.avgStdValue())
            .avgMissingRate(summaryMetrics.avgMissingRate())
            .avgStatOutlierRate(summaryMetrics.avgStatOutlierRate())
            .avgPhysicalOutlierRate(summaryMetrics.avgPhysicalOutlierRate())
            .qualityLevelStatistics(qualityLevelStatistics)
            .batchStatistics(batchStatistics)
            .build();
    }

    private void assertMetricCode(Long processId, String metricCode) {
        Long count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM (
                SELECT metric_code
                FROM dim_process_metric
                WHERE process_id = ? AND metric_code = ?
                UNION
                SELECT metric_code
                FROM fact_process_metric_value
                WHERE process_id = ? AND metric_code = ?
            ) t
            """,
            Long.class,
            processId,
            metricCode,
            processId,
            metricCode
        );
        if (count == null || count == 0L) {
            throw new BusinessException("未找到当前工序下的数据类别");
        }
    }

    private String queryMetricName(Long processId, String metricCode) {
        String metricName = jdbcTemplate.queryForObject(
            "SELECT metric_name FROM dim_process_metric WHERE process_id = ? AND metric_code = ?",
            String.class,
            processId,
            metricCode
        );
        return metricName == null ? metricCode : metricName;
    }

    private BusinessProcessDefinition resolveProcessByBatch(String batchId) {
        Long resolvedProcessId = jdbcTemplate.queryForObject(
            "SELECT process_id FROM fact_process_metric_value WHERE batch_no = ? LIMIT 1",
            Long.class,
            batchId
        );
        if (resolvedProcessId == null) {
            throw new BusinessException("未找到当前批次所属工序");
        }
        return businessProcessRegistry.getById(resolvedProcessId);
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp.toLocalDateTime().format(SHORT_TIME_FORMATTER);
    }

    private String formatFullTimestamp(Timestamp timestamp) {
        return timestamp.toLocalDateTime().format(FULL_TIME_FORMATTER);
    }

    private Double readNullableDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private double round(double value) {
        return Double.parseDouble(String.format(Locale.US, "%.4f", value));
    }

    private record SummaryMetrics(
        int totalBatchCount,
        int totalDataPointCount,
        double avgMeanValue,
        double avgStdValue,
        double avgMissingRate,
        double avgStatOutlierRate,
        double avgPhysicalOutlierRate
    ) {
    }
}
