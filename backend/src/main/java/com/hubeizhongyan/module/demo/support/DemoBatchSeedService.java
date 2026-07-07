/*
 * 文件名称：DemoBatchSeedService
 * 文件说明：历史分析演示数据生成服务，负责写入工序、变量、批次、时序明细和统计结果，是最小业务闭环的数据基础。
 * 主要职责：
 * 1. 初始化演示主数据。
 * 2. 生成批次级时序明细数据。
 * 3. 生成批次级统计结果数据。
 * 4. 为导入演示流程复用批次生成能力。
 * 开发者：czd
 */
package com.hubeizhongyan.module.demo.support;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DemoBatchSeedService {

    private final JdbcTemplate jdbcTemplate;

    // 初始化或补充演示主数据时，统一通过这里写入工序，避免多处散落插入逻辑。
    public void insertProcess(Long id, String code, String name, String description, LocalDateTime now) {
        jdbcTemplate.update(
            """
            INSERT INTO process_info (id, process_code, process_name, description, enabled, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            id, code, name, description, true, Timestamp.valueOf(now), Timestamp.valueOf(now)
        );
    }

    // 变量元数据是后续生成批次、趋势图和统计结果的基础配置。
    public void insertVariable(Long id, Long processId, String code, String name, String unit,
                               String type, double min, double max, LocalDateTime now) {
        jdbcTemplate.update(
            """
            INSERT INTO sensor_variable (id, process_id, variable_code, variable_name, unit, variable_type,
                                         physical_min, physical_max, enabled, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id, processId, code, name, unit, type, min, max, true, Timestamp.valueOf(now), Timestamp.valueOf(now)
        );
    }

    // 批次生成统一支持传入质量等级，方便首页、批次分析和质量分析共用同一批演示数据源。
    public void seedBatch(Long batchId, Long processId, String batchCode, String qualityLevel,
                          LocalDateTime startTime, List<VariableSeed> seeds) {
        int pointsPerVariable = 18;
        LocalDateTime endTime = startTime.plusMinutes((pointsPerVariable - 1L) * 5L);

        jdbcTemplate.update(
            """
            INSERT INTO batch_info (id, process_id, batch_code, start_time, end_time, data_point_count, status,
                                    quality_level, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            batchId, processId, batchCode, Timestamp.valueOf(startTime), Timestamp.valueOf(endTime),
            seeds.size() * pointsPerVariable, "已完成", qualityLevel, Timestamp.valueOf(startTime), Timestamp.valueOf(startTime)
        );

        long sensorDataId = nextId("sensor_data");
        long statisticId = nextId("batch_statistic");

        for (VariableSeed seed : seeds) {
            // 先得到一条变量的完整时序，再反推统计指标，保证“明细先于统计”的业务顺序。
            List<DataPoint> points = buildSeries(seed, startTime, pointsPerVariable);
            List<Double> cleanValues = points.stream()
                .filter(point -> point.cleanValue != null)
                .map(point -> point.cleanValue)
                .toList();

            double mean = cleanValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = cleanValues.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0);
            double std = Math.sqrt(variance);

            for (DataPoint point : points) {
                // 标准化值在入库时一并计算，避免查询阶段重复运算。
                Double standardValue = point.cleanValue == null || std == 0
                    ? null
                    : round((point.cleanValue - mean) / std);
                jdbcTemplate.update(
                    """
                    INSERT INTO sensor_data (id, collect_time, process_id, batch_id, variable_id, raw_value, clean_value,
                                             standard_value, is_missing, is_stat_outlier, is_physical_outlier, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    sensorDataId++,
                    Timestamp.valueOf(point.collectTime),
                    processId,
                    batchId,
                    seed.variableId,
                    point.rawValue,
                    point.cleanValue,
                    standardValue,
                    point.missing,
                    point.statOutlier,
                    point.physicalOutlier,
                    Timestamp.valueOf(point.collectTime)
                );
            }

            // 统计表先保留前端已使用的核心指标，后续可在此基础上继续扩展。
            double min = cleanValues.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = cleanValues.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            long missingCount = points.stream().filter(point -> point.missing).count();
            long statOutlierCount = points.stream().filter(point -> point.statOutlier).count();
            long physicalOutlierCount = points.stream().filter(point -> point.physicalOutlier).count();

            jdbcTemplate.update(
                """
                INSERT INTO batch_statistic (id, batch_id, variable_id, mean_value, std_value, min_value, max_value,
                                             missing_rate, stat_outlier_rate, physical_outlier_rate, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                statisticId++,
                batchId,
                seed.variableId,
                round(mean),
                round(std),
                round(min),
                round(max),
                round(missingCount / (double) pointsPerVariable),
                round(statOutlierCount / (double) pointsPerVariable),
                round(physicalOutlierCount / (double) pointsPerVariable),
                Timestamp.valueOf(endTime)
            );
        }
    }

    // 演示库没有独立维护序列，当前用 max(id)+1 足以支撑最小业务闭环。
    public long nextId(String tableName) {
        Long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM " + tableName, Long.class);
        return id == null ? 1L : id;
    }

    // 这里按“基础值 + 波动 + 漂移 + 单点异常”的方式构造一条可视化效果明显的模拟曲线。
    private List<DataPoint> buildSeries(VariableSeed seed, LocalDateTime startTime, int points) {
        List<DataPoint> result = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            LocalDateTime collectTime = startTime.plusMinutes(i * 5L);
            double wave = Math.sin(i / 2.5D) * seed.wave;
            double drift = i * 0.18D;
            double baseValue = seed.base + wave + drift;

            boolean missing = i == 3;
            boolean statOutlier = i == seed.outlierIndex;
            boolean physicalOutlier = i == seed.outlierIndex;

            Double rawValue = missing ? null : round(baseValue + (i % 4) * 0.12D);
            if (statOutlier && rawValue != null) {
                // 原始值保留异常抬升，清洗值回落到可继续分析的水平。
                rawValue = round(rawValue + seed.wave * 4.5D);
            }

            Double cleanValue = rawValue;
            if (statOutlier && rawValue != null) {
                cleanValue = round(seed.base + drift);
            }

            result.add(new DataPoint(collectTime, rawValue, cleanValue, missing, statOutlier, physicalOutlier));
        }
        return result;
    }

    // 统一保留四位小数，确保前端图表与表格展示稳定。
    private double round(double value) {
        return Double.parseDouble(String.format(Locale.US, "%.4f", value));
    }

    public record VariableSeed(Long variableId, double base, double wave, int outlierIndex) {
    }

    private record DataPoint(LocalDateTime collectTime, Double rawValue, Double cleanValue,
                             boolean missing, boolean statOutlier, boolean physicalOutlier) {
    }
}
