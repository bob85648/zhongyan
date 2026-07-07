/*
 * 文件说明：HistoricalDataProcessBackfillRunner
 * 文件用途：负责在系统启动后对历史数据执行工序归属回填迁移，将旧版本导入产生的空 process_id
 *          数据补齐到当前多工序模型中，保证首页、批次分析、数据类别管理等模块可以按照工序维度
 *          正常查询和展示历史数据。该组件采用幂等化设计，只处理 process_id 为空的数据，适合在
 *          开发、联调和生产环境中重复启动执行。
 * 业务说明：
 * 1. 优先基于 import_task.generated_batch_code 回填批次级历史事实数据。
 * 2. 其次基于 uploaded_file.file_name 与 source_file 的映射回填来源文件级数据。
 * 3. 在事实表回填完成后，再同步补齐批次统计表与指标维度表中的 process_id。
 * 4. 对无法唯一判定工序的数据保留为空，避免错误归属污染正式历史数据。
 * 开发者：czd
 */
package com.hubeizhongyan.common.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HistoricalDataProcessBackfillRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("fact_process_metric_value")
            || !tableExists("ads_batch_metric_stat")
            || !tableExists("dim_process_metric")
            || !tableExists("import_task")
            || !tableExists("uploaded_file")) {
            log.info("历史数据工序回填跳过，原因：正式分析表或导入任务表尚未初始化完成。");
            return;
        }

        long factNullBefore = countNullProcessId("fact_process_metric_value");
        long statNullBefore = countNullProcessId("ads_batch_metric_stat");
        long metricNullBefore = countNullProcessId("dim_process_metric");
        if (factNullBefore == 0 && statNullBefore == 0 && metricNullBefore == 0) {
            log.info("历史数据工序回填检查完成，未发现需要处理的旧数据。");
            return;
        }

        int factByBatch = backfillFactByBatchCode();
        int factByFile = backfillFactBySourceFile();
        int statByFact = backfillBatchStatByFact();
        int statByFile = backfillBatchStatBySourceFile();
        int metricByFact = backfillMetricByFact();
        int metricInserted = insertMissingMetricsFromFact();

        long factNullAfter = countNullProcessId("fact_process_metric_value");
        long statNullAfter = countNullProcessId("ads_batch_metric_stat");
        long metricNullAfter = countNullProcessId("dim_process_metric");

        log.info(
            "历史数据工序回填完成：fact[批次={}, 文件={}, 剩余空值={} -> {}], stat[事实={}, 文件={}, 剩余空值={} -> {}], metric[事实={}, 新增={}, 剩余空值={} -> {}]",
            factByBatch,
            factByFile,
            factNullBefore,
            factNullAfter,
            statByFact,
            statByFile,
            statNullBefore,
            statNullAfter,
            metricByFact,
            metricInserted,
            metricNullBefore,
            metricNullAfter
        );
    }

    // 开发者czd：优先按导入任务生成的批次号回填事实表，命中率最高且业务语义最稳定。
    private int backfillFactByBatchCode() {
        return jdbcTemplate.update(
            """
            UPDATE fact_process_metric_value AS fact
               SET process_id = task.process_id
              FROM import_task AS task
             WHERE fact.process_id IS NULL
               AND task.generated_batch_code IS NOT NULL
               AND task.process_id IS NOT NULL
               AND fact.batch_no = task.generated_batch_code
            """
        );
    }

    // 开发者czd：当批次号无法命中时，再基于唯一来源文件归属回填事实表，兼容早期导入历史。
    private int backfillFactBySourceFile() {
        return jdbcTemplate.update(
            """
            UPDATE fact_process_metric_value AS fact
               SET process_id = file_map.process_id
              FROM (
                    SELECT file_name, MAX(process_id) AS process_id
                      FROM uploaded_file
                     WHERE process_id IS NOT NULL
                     GROUP BY file_name
                    HAVING COUNT(DISTINCT process_id) = 1
                   ) AS file_map
             WHERE fact.process_id IS NULL
               AND fact.source_file = file_map.file_name
            """
        );
    }

    // 开发者czd：事实表回填后，优先按批次+指标维度同步补齐批次统计表，避免图表聚合查不到数据。
    private int backfillBatchStatByFact() {
        return jdbcTemplate.update(
            """
            UPDATE ads_batch_metric_stat AS stat
               SET process_id = fact_map.process_id
              FROM (
                    SELECT batch_no, metric_code, MAX(process_id) AS process_id
                      FROM fact_process_metric_value
                     WHERE process_id IS NOT NULL
                       AND batch_no IS NOT NULL
                     GROUP BY batch_no, metric_code
                    HAVING COUNT(DISTINCT process_id) = 1
                   ) AS fact_map
             WHERE stat.process_id IS NULL
               AND stat.batch_no = fact_map.batch_no
               AND stat.metric_code = fact_map.metric_code
            """
        );
    }

    // 开发者czd：为批次统计表补充来源文件兜底映射，处理早期统计表先于事实表生成的历史数据。
    private int backfillBatchStatBySourceFile() {
        return jdbcTemplate.update(
            """
            UPDATE ads_batch_metric_stat AS stat
               SET process_id = file_map.process_id
              FROM (
                    SELECT file_name, MAX(process_id) AS process_id
                      FROM uploaded_file
                     WHERE process_id IS NOT NULL
                     GROUP BY file_name
                    HAVING COUNT(DISTINCT process_id) = 1
                   ) AS file_map
             WHERE stat.process_id IS NULL
               AND stat.source_file = file_map.file_name
            """
        );
    }

    // 开发者czd：维度表只在指标编码能够唯一映射到某个工序时才回填，避免同名指标跨工序误归属。
    private int backfillMetricByFact() {
        return jdbcTemplate.update(
            """
            UPDATE dim_process_metric AS metric
               SET process_id = fact_map.process_id
              FROM (
                    SELECT metric_code, MAX(process_id) AS process_id
                      FROM fact_process_metric_value
                     WHERE process_id IS NOT NULL
                     GROUP BY metric_code
                    HAVING COUNT(DISTINCT process_id) = 1
                   ) AS fact_map
             WHERE metric.process_id IS NULL
               AND metric.metric_code = fact_map.metric_code
            """
        );
    }

    // 开发者czd：当旧维度表缺失记录时，直接依据事实表补建工序指标维度，确保前端筛选和管理页面可用。
    private int insertMissingMetricsFromFact() {
        return jdbcTemplate.update(
            """
            WITH missing_metric AS (
                SELECT fact.process_id,
                       fact.metric_code,
                       MAX(fact.metric_name) AS metric_name
                  FROM fact_process_metric_value AS fact
                 WHERE fact.process_id IS NOT NULL
                 GROUP BY fact.process_id, fact.metric_code
                HAVING NOT EXISTS (
                    SELECT 1
                      FROM dim_process_metric AS metric
                     WHERE metric.process_id = fact.process_id
                       AND metric.metric_code = fact.metric_code
                )
            ),
            next_id AS (
                SELECT COALESCE(MAX(id), 0) AS base_id
                  FROM dim_process_metric
            )
            INSERT INTO dim_process_metric (
                id,
                process_id,
                metric_code,
                metric_name,
                source_column_name,
                metric_order,
                created_at
            )
            SELECT next_id.base_id + ROW_NUMBER() OVER (ORDER BY metric.process_id, metric.metric_code),
                   metric.process_id,
                   metric.metric_code,
                   metric.metric_name,
                   metric.metric_name,
                   COALESCE(
                       (
                           SELECT MAX(existing.metric_order)
                             FROM dim_process_metric AS existing
                            WHERE existing.process_id = metric.process_id
                       ),
                       0
                   ) + ROW_NUMBER() OVER (PARTITION BY metric.process_id ORDER BY metric.metric_code),
                   CURRENT_TIMESTAMP
              FROM missing_metric AS metric
              CROSS JOIN next_id
            """
        );
    }

    // 开发者czd：统一统计指定表中 process_id 为空的数据量，用于启动时判断和回填结果审计。
    private long countNullProcessId(String tableName) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE process_id IS NULL",
            Long.class
        );
        return count == null ? 0L : count;
    }

    // 开发者czd：启动时先确认业务表存在，避免首次建库或局部环境下直接执行回填 SQL 造成异常。
    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                  FROM information_schema.tables
                 WHERE table_schema = current_schema()
                   AND table_name = ?
            )
            """,
            Boolean.class,
            tableName
        );
        return Boolean.TRUE.equals(exists);
    }
}
