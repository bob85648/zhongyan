/*
 * 文件说明：ProcessManagementServiceImpl
 * 文件业务说明：工序管理服务实现，负责按正式分析库中的多工序数据汇总工序级统计信息，为工序管理页面
 *          提供工序概览列表与详情能力。
 * 业务职责：
 * 1. 返回系统支持的工序列表。
 * 2. 按工序聚合批次量、测点量、数据类别量和采集时间范围。
 * 3. 保证工序统计与导入后的正式分析数据一致。
 * 开发者：czd
 */
package com.hubeizhongyan.module.master.service.impl;

import com.hubeizhongyan.common.exception.BusinessException;
import com.hubeizhongyan.common.support.BusinessProcessRegistry;
import com.hubeizhongyan.common.support.BusinessProcessRegistry.BusinessProcessDefinition;
import com.hubeizhongyan.module.master.dto.ProcessDetailResponse;
import com.hubeizhongyan.module.master.dto.ProcessSummaryResponse;
import com.hubeizhongyan.module.master.service.ProcessManagementService;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProcessManagementServiceImpl implements ProcessManagementService {

    private static final DateTimeFormatter FULL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final BusinessProcessRegistry businessProcessRegistry;

    @Override
    public List<ProcessSummaryResponse> listProcesses() {
        return businessProcessRegistry.listAll().stream()
            .map(definition -> {
                ProcessStatistics statistics = queryProcessStatistics(definition.id());
                return ProcessSummaryResponse.builder()
                    .processId(definition.id())
                    .processCode(definition.processCode())
                    .processName(definition.processName())
                    .status("ENABLED")
                    .variableCount(statistics.variableCount())
                    .batchCount(statistics.batchCount())
                    .dataPointCount(statistics.dataPointCount())
                    .lastCollectTime(formatTimestamp(statistics.lastCollectTime()))
                    .build();
            })
            .toList();
    }

    @Override
    public ProcessDetailResponse getProcessDetail(Long requestedProcessId) {
        BusinessProcessDefinition definition = businessProcessRegistry.getById(requestedProcessId);
        ProcessStatistics statistics = queryProcessStatistics(requestedProcessId);
        Integer sourceFileCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT source_file) FROM fact_process_metric_value WHERE process_id = ?",
            Integer.class,
            requestedProcessId
        );
        return ProcessDetailResponse.builder()
            .processId(definition.id())
            .processCode(definition.processCode())
            .processName(definition.processName())
            .status("ENABLED")
            .variableCount(statistics.variableCount())
            .batchCount(statistics.batchCount())
            .dataPointCount(statistics.dataPointCount())
            .sourceFileCount(defaultInt(sourceFileCount))
            .firstCollectTime(formatTimestamp(statistics.firstCollectTime()))
            .lastCollectTime(formatTimestamp(statistics.lastCollectTime()))
            .description(definition.description())
            .build();
    }

    private ProcessStatistics queryProcessStatistics(Long processId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                (SELECT COUNT(*) FROM dim_process_metric WHERE process_id = ?) AS variable_count,
                (SELECT COUNT(DISTINCT batch_no) FROM fact_process_metric_value WHERE process_id = ?) AS batch_count,
                (SELECT COUNT(*) FROM fact_process_metric_value WHERE process_id = ?) AS data_point_count,
                MIN(process_time) AS first_collect_time,
                MAX(process_time) AS last_collect_time
            FROM fact_process_metric_value
            WHERE process_id = ?
            """,
            (rs, rowNum) -> new ProcessStatistics(
                defaultInt(rs.getObject("variable_count")),
                defaultInt(rs.getObject("batch_count")),
                defaultInt(rs.getObject("data_point_count")),
                rs.getTimestamp("first_collect_time"),
                rs.getTimestamp("last_collect_time")
            ),
            processId,
            processId,
            processId,
            processId
        );
    }

    private Integer defaultInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? "--" : timestamp.toLocalDateTime().format(FULL_TIME_FORMATTER);
    }

    private record ProcessStatistics(
        Integer variableCount,
        Integer batchCount,
        Integer dataPointCount,
        Timestamp firstCollectTime,
        Timestamp lastCollectTime
    ) {
    }
}
