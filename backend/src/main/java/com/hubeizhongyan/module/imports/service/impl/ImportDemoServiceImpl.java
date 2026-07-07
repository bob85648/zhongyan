/*
 * 文件说明：ImportDemoServiceImpl
 * 文件业务说明：文件导入业务服务实现，负责将烟草工序宽表 Excel 按企业导入流程完成任务登记、模板解析、
 *          数据类别注册、暂存落库、事实表写入和批次统计汇总，打通“上传文件 -> 正式入库 -> 页面分析展示”
 *          的真实业务链路。
 * 业务职责：
 * 1. 校验上传文件与工序上下文。
 * 2. 解析 Excel 宽表并执行基础清洗。
 * 3. 将导入结果写入暂存表、事实表和统计表。
 * 4. 维护导入任务状态、成功失败数量与提示信息。
 * 开发者：czd
 */
package com.hubeizhongyan.module.imports.service.impl;

import com.hubeizhongyan.common.exception.BusinessException;
import com.hubeizhongyan.common.support.BusinessProcessRegistry;
import com.hubeizhongyan.common.support.BusinessProcessRegistry.BusinessProcessDefinition;
import com.hubeizhongyan.module.imports.dto.ImportTaskResponse;
import com.hubeizhongyan.module.imports.dto.UploadImportResponse;
import com.hubeizhongyan.module.imports.service.ImportDemoService;
import com.hubeizhongyan.module.imports.support.ExcelImportParser;
import com.hubeizhongyan.module.imports.support.ExcelImportParser.ParsedMetricRecord;
import com.hubeizhongyan.module.imports.support.ExcelImportParser.ParsedWorkbook;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ImportDemoServiceImpl implements ImportDemoService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String STATUS_PROCESSING = "处理中";
    private static final String STATUS_SUCCESS = "成功";
    private static final String STATUS_PARTIAL_SUCCESS = "部分成功";
    private static final String STATUS_FAILED = "失败";
    private static final String STAGE_STATUS_SUCCESS = "SUCCESS";
    private static final String STAGE_STATUS_FAILED = "FAILED";

    private final JdbcTemplate jdbcTemplate;
    private final ExcelImportParser excelImportParser;
    private final BusinessProcessRegistry businessProcessRegistry;

    @PostConstruct
    public void initializeImportTables() {
        ensureImportTables();
        initializeBuiltinProcesses();
    }

    @Override
    @Transactional
    public UploadImportResponse upload(Long processId, MultipartFile file) {
        ensureImportTables();
        BusinessProcessDefinition processDefinition = businessProcessRegistry.getById(processId);
        validateFile(file);

        LocalDateTime now = LocalDateTime.now();
        long fileId = nextId("uploaded_file");
        long taskId = nextId("import_task");
        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "unknown.xlsx");
        String fileType = extractFileType(fileName);

        jdbcTemplate.update(
            """
            INSERT INTO uploaded_file (id, process_id, file_name, file_size, file_type, uploaded_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            fileId,
            processId,
            fileName,
            file.getSize(),
            fileType,
            Timestamp.valueOf(now)
        );

        jdbcTemplate.update(
            """
            INSERT INTO import_task (id, file_id, process_id, process_name, generated_batch_code, status,
                                     total_rows, success_rows, failed_rows, message, created_at, finished_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            taskId,
            fileId,
            processId,
            processDefinition.processName(),
            null,
            STATUS_PROCESSING,
            0,
            0,
            0,
            "文件已接收，开始解析并导入正式分析数据",
            Timestamp.valueOf(now),
            null
        );

        try {
            ParsedWorkbook workbook = excelImportParser.parse(file);
            List<NormalizedMetricRecord> normalizedRecords = normalizeRecords(taskId, processDefinition, fileName, workbook);
            if (normalizedRecords.isEmpty()) {
                throw new BusinessException("导入文件未识别到可入库的测点数据");
            }

            registerMetrics(processId, normalizedRecords);
            persistStageRecords(taskId, fileName, workbook.sheetName(), normalizedRecords, now);

            Set<String> batchNos = collectBatchNos(normalizedRecords, fileName);
            replaceBatchFacts(processId, batchNos);
            insertFactRecords(processId, normalizedRecords, fileName, workbook.sheetName(), now);
            insertBatchStatistics(processId, normalizedRecords, fileName, now);

            ImportSummary summary = buildSummary(normalizedRecords, workbook.dataRowCount(), batchNos);
            updateTaskResult(taskId, summary, LocalDateTime.now());

            return UploadImportResponse.builder()
                .taskId(taskId)
                .generatedBatchCode(summary.generatedBatchCode())
                .status(summary.status())
                .message(summary.message())
                .build();
        } catch (RuntimeException exception) {
            updateTaskFailure(taskId, exception.getMessage(), LocalDateTime.now());
            throw exception;
        }
    }

    @Override
    public List<ImportTaskResponse> listTasks() {
        ensureImportTables();
        return jdbcTemplate.query(
            """
            SELECT t.id, t.process_name, f.file_name, t.generated_batch_code, t.status,
                   t.total_rows, t.success_rows, t.failed_rows, t.message, t.created_at, t.finished_at
            FROM import_task t
            JOIN uploaded_file f ON f.id = t.file_id
            ORDER BY t.created_at DESC
            """,
            (rs, rowNum) -> ImportTaskResponse.builder()
                .id(rs.getLong("id"))
                .processName(rs.getString("process_name"))
                .fileName(rs.getString("file_name"))
                .generatedBatchCode(rs.getString("generated_batch_code"))
                .status(rs.getString("status"))
                .totalRows(rs.getInt("total_rows"))
                .successRows(rs.getInt("success_rows"))
                .failedRows(rs.getInt("failed_rows"))
                .message(rs.getString("message"))
                .createdAt(formatTimestamp(rs.getTimestamp("created_at")))
                .finishedAt(formatTimestamp(rs.getTimestamp("finished_at")))
                .build()
        );
    }

    // 开发者 czd：导入前先确保任务表与暂存表存在，避免正式库只初始化分析主表时直接报错。
    private void ensureImportTables() {
        try {
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS process_info (
                    id BIGINT PRIMARY KEY,
                    process_code VARCHAR(64) NOT NULL UNIQUE,
                    process_name VARCHAR(128) NOT NULL,
                    description VARCHAR(255),
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """
            );
            jdbcTemplate.execute("ALTER TABLE dim_process_metric ADD COLUMN IF NOT EXISTS process_id BIGINT");
            jdbcTemplate.execute("ALTER TABLE ads_batch_metric_stat ADD COLUMN IF NOT EXISTS process_id BIGINT");
            jdbcTemplate.execute("ALTER TABLE fact_process_metric_value ADD COLUMN IF NOT EXISTS process_id BIGINT");
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS uploaded_file (
                    id BIGINT PRIMARY KEY,
                    process_id BIGINT NOT NULL,
                    file_name VARCHAR(255) NOT NULL,
                    file_size BIGINT NOT NULL,
                    file_type VARCHAR(32) NOT NULL,
                    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """
            );
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS import_task (
                    id BIGINT PRIMARY KEY,
                    file_id BIGINT NOT NULL,
                    process_id BIGINT NOT NULL,
                    process_name VARCHAR(128) NOT NULL,
                    generated_batch_code VARCHAR(255),
                    status VARCHAR(32) NOT NULL,
                    total_rows INT NOT NULL,
                    success_rows INT NOT NULL,
                    failed_rows INT NOT NULL,
                    message VARCHAR(500),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    finished_at TIMESTAMP
                )
                """
            );
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS stg_import_sensor_record (
                    id BIGINT PRIMARY KEY,
                    import_task_id BIGINT NOT NULL,
                    source_file_name VARCHAR(255) NOT NULL,
                    source_sheet VARCHAR(128),
                    source_row_no INT NOT NULL,
                    process_time TIMESTAMP,
                    batch_no VARCHAR(128),
                    metric_code VARCHAR(32) NOT NULL,
                    metric_name VARCHAR(255) NOT NULL,
                    metric_value DECIMAL(18, 6),
                    validity_label VARCHAR(64),
                    raw_text_value VARCHAR(255),
                    import_status VARCHAR(32) NOT NULL,
                    error_message VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """
            );
        } catch (DataAccessException exception) {
            throw new BusinessException("初始化导入任务表失败: " + exception.getMostSpecificCause().getMessage());
        }
    }

    // 开发者 czd：系统内置三条正式工序定义，启动时自动同步到工序主数据表，供前端下拉与导入任务共用。
    private void initializeBuiltinProcesses() {
        for (BusinessProcessDefinition definition : businessProcessRegistry.listAll()) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM process_info WHERE id = ?",
                Integer.class,
                definition.id()
            );
            if (count != null && count > 0) {
                jdbcTemplate.update(
                    """
                    UPDATE process_info
                       SET process_code = ?, process_name = ?, description = ?, enabled = TRUE, updated_at = ?
                     WHERE id = ?
                    """,
                    definition.processCode(),
                    definition.processName(),
                    definition.description(),
                    Timestamp.valueOf(LocalDateTime.now()),
                    definition.id()
                );
                continue;
            }
            jdbcTemplate.update(
                """
                INSERT INTO process_info (id, process_code, process_name, description, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, TRUE, ?, ?)
                """,
                definition.id(),
                definition.processCode(),
                definition.processName(),
                definition.description(),
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now())
            );
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请先选择需要导入的 Excel 文件");
        }
        String suffix = extractFileType(Objects.requireNonNullElse(file.getOriginalFilename(), ""));
        if (!List.of("xlsx", "xls").contains(suffix)) {
            throw new BusinessException("当前仅支持 xlsx / xls 格式文件导入");
        }
    }

    // 开发者 czd：这里把 Excel 记录标准化为统一导入对象，同时用“有效性”保留业务标签，供后续扩展规则过滤。
    private List<NormalizedMetricRecord> normalizeRecords(Long taskId, BusinessProcessDefinition processDefinition,
                                                          String fileName, ParsedWorkbook workbook) {
        List<NormalizedMetricRecord> normalizedRecords = new ArrayList<>();
        String importRunId = "IMPORT-" + taskId;
        String defaultBatchNo = stripExtension(fileName);

        for (ParsedMetricRecord record : workbook.records()) {
            String batchNo = record.batchNo().isBlank() ? defaultBatchNo : record.batchNo().trim();
            String metricCode = buildMetricCode(processDefinition.processCode(), record.metricName());
            try {
                BigDecimal metricValue = excelImportParser.parseMetricValue(record.rawValue(), record.rowNo(), record.metricName());
                normalizedRecords.add(NormalizedMetricRecord.success(
                    importRunId,
                    record.rowNo(),
                    record.processTime(),
                    batchNo,
                    metricCode,
                    record.metricName(),
                    metricValue,
                    normalizeValidity(record.validityLabel()),
                    record.rawValue()
                ));
            } catch (BusinessException exception) {
                normalizedRecords.add(NormalizedMetricRecord.failed(
                    importRunId,
                    record.rowNo(),
                    record.processTime(),
                    batchNo,
                    metricCode,
                    record.metricName(),
                    normalizeValidity(record.validityLabel()),
                    record.rawValue(),
                    exception.getMessage()
                ));
            }
        }
        return normalizedRecords;
    }

    private void registerMetrics(Long processId, List<NormalizedMetricRecord> normalizedRecords) {
        Map<String, String> existingMetrics = new HashMap<>();
        jdbcTemplate.query(
            "SELECT metric_code, metric_name FROM dim_process_metric WHERE process_id = ?",
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                existingMetrics.put(rs.getString("metric_name"), rs.getString("metric_code")),
            processId
        );

        long nextMetricId = nextId("dim_process_metric");
        int nextOrder = queryNextMetricOrder();
        for (NormalizedMetricRecord record : normalizedRecords) {
            if (existingMetrics.containsKey(record.metricName())) {
                continue;
            }
            jdbcTemplate.update(
                """
                INSERT INTO dim_process_metric (id, process_id, metric_code, metric_name, source_column_name, metric_order, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                nextMetricId++,
                processId,
                record.metricCode(),
                record.metricName(),
                record.metricName(),
                nextOrder++,
                Timestamp.valueOf(LocalDateTime.now())
            );
            existingMetrics.put(record.metricName(), record.metricCode());
        }
    }

    private void persistStageRecords(Long taskId, String fileName, String sheetName,
                                     List<NormalizedMetricRecord> normalizedRecords, LocalDateTime now) {
        long stageId = nextId("stg_import_sensor_record");
        for (NormalizedMetricRecord record : normalizedRecords) {
            jdbcTemplate.update(
                """
                INSERT INTO stg_import_sensor_record (id, import_task_id, source_file_name, source_sheet, source_row_no,
                                                      process_time, batch_no, metric_code, metric_name, metric_value,
                                                      validity_label, raw_text_value, import_status, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                stageId++,
                taskId,
                fileName,
                sheetName,
                record.rowNo(),
                Timestamp.valueOf(record.processTime()),
                record.batchNo(),
                record.metricCode(),
                record.metricName(),
                record.metricValue(),
                record.validityLabel(),
                truncate(record.rawTextValue(), 255),
                record.success() ? STAGE_STATUS_SUCCESS : STAGE_STATUS_FAILED,
                truncate(record.errorMessage(), 255),
                Timestamp.valueOf(now)
            );
        }
    }

    private Set<String> collectBatchNos(List<NormalizedMetricRecord> normalizedRecords, String fileName) {
        Set<String> batchNos = new LinkedHashSet<>();
        String defaultBatchNo = stripExtension(fileName);
        for (NormalizedMetricRecord record : normalizedRecords) {
            if (record.success()) {
                batchNos.add(record.batchNo().isBlank() ? defaultBatchNo : record.batchNo());
            }
        }
        return batchNos;
    }

    // 开发者 czd：同一批次重复导入时先删再写，确保分析页读取的永远是最新一次导入结果，避免批次数据重复累计。
    private void replaceBatchFacts(Long processId, Set<String> batchNos) {
        for (String batchNo : batchNos) {
            jdbcTemplate.update("DELETE FROM ads_batch_metric_stat WHERE process_id = ? AND batch_no = ?", processId, batchNo);
            jdbcTemplate.update("DELETE FROM fact_process_metric_value WHERE process_id = ? AND batch_no = ?", processId, batchNo);
        }
    }

    private void insertFactRecords(Long processId, List<NormalizedMetricRecord> normalizedRecords, String fileName,
                                   String sheetName, LocalDateTime now) {
        long factId = nextId("fact_process_metric_value");
        for (NormalizedMetricRecord record : normalizedRecords) {
            if (!record.success()) {
                continue;
            }
            jdbcTemplate.update(
                """
                INSERT INTO fact_process_metric_value (id, process_id, import_run_id, source_file, source_sheet, process_time, batch_no,
                                                       metric_code, metric_name, metric_value, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                factId++,
                processId,
                record.importRunId(),
                fileName,
                sheetName,
                Timestamp.valueOf(record.processTime()),
                record.batchNo(),
                record.metricCode(),
                record.metricName(),
                record.metricValue(),
                Timestamp.valueOf(now)
            );
        }
    }

    private void insertBatchStatistics(Long processId, List<NormalizedMetricRecord> normalizedRecords, String fileName, LocalDateTime now) {
        Map<BatchMetricKey, List<NormalizedMetricRecord>> groupedRecords = new HashMap<>();
        for (NormalizedMetricRecord record : normalizedRecords) {
            if (!record.success()) {
                continue;
            }
            groupedRecords.computeIfAbsent(
                new BatchMetricKey(record.batchNo(), record.metricCode(), record.metricName(), record.importRunId()),
                key -> new ArrayList<>()
            ).add(record);
        }

        long statisticId = nextId("ads_batch_metric_stat");
        for (Map.Entry<BatchMetricKey, List<NormalizedMetricRecord>> entry : groupedRecords.entrySet()) {
            List<NormalizedMetricRecord> records = entry.getValue();
            records.sort(Comparator.comparing(NormalizedMetricRecord::processTime));

            List<BigDecimal> metricValues = records.stream()
                .map(NormalizedMetricRecord::metricValue)
                .filter(Objects::nonNull)
                .toList();

            BigDecimal avgValue = average(metricValues);
            BigDecimal varianceValue = variance(metricValues, avgValue);
            BigDecimal stddevValue = sqrt(varianceValue);
            BigDecimal minValue = metricValues.stream().min(Comparator.naturalOrder()).orElse(null);
            BigDecimal maxValue = metricValues.stream().max(Comparator.naturalOrder()).orElse(null);

            jdbcTemplate.update(
                """
                INSERT INTO ads_batch_metric_stat (id, process_id, import_run_id, source_file, batch_no, metric_code, metric_name,
                                                  sample_count, avg_value, variance_value, stddev_value, min_value, max_value,
                                                  start_time, end_time, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                statisticId++,
                processId,
                entry.getKey().importRunId(),
                fileName,
                entry.getKey().batchNo(),
                entry.getKey().metricCode(),
                entry.getKey().metricName(),
                records.size(),
                avgValue,
                varianceValue,
                stddevValue,
                minValue,
                maxValue,
                Timestamp.valueOf(records.get(0).processTime()),
                Timestamp.valueOf(records.get(records.size() - 1).processTime()),
                Timestamp.valueOf(now)
            );
        }
    }

    private ImportSummary buildSummary(List<NormalizedMetricRecord> normalizedRecords, int sourceRowCount, Set<String> batchNos) {
        Set<Integer> failedRows = new LinkedHashSet<>();
        Set<Integer> successRows = new LinkedHashSet<>();
        long successPointCount = 0L;
        long failedPointCount = 0L;

        for (NormalizedMetricRecord record : normalizedRecords) {
            if (record.success()) {
                successPointCount++;
                successRows.add(record.rowNo());
            } else {
                failedPointCount++;
                failedRows.add(record.rowNo());
            }
        }

        String status = failedPointCount == 0 ? STATUS_SUCCESS : STATUS_PARTIAL_SUCCESS;
        if (successPointCount == 0) {
            status = STATUS_FAILED;
        }
        String generatedBatchCode = summarizeBatchNos(batchNos);
        String message = "已解析 " + sourceRowCount + " 行，成功写入 " + successPointCount + " 条测点数据";
        if (failedPointCount > 0) {
            message = message + "，失败 " + failedPointCount + " 条，失败源行数 " + failedRows.size() + " 行";
        }
        return new ImportSummary(
            generatedBatchCode,
            status,
            sourceRowCount,
            successRows.size(),
            failedRows.size(),
            truncate(message, 500)
        );
    }

    private void updateTaskResult(Long taskId, ImportSummary summary, LocalDateTime finishedAt) {
        jdbcTemplate.update(
            """
            UPDATE import_task
               SET generated_batch_code = ?, status = ?, total_rows = ?, success_rows = ?,
                   failed_rows = ?, message = ?, finished_at = ?
             WHERE id = ?
            """,
            summary.generatedBatchCode(),
            summary.status(),
            summary.totalRows(),
            summary.successRows(),
            summary.failedRows(),
            summary.message(),
            Timestamp.valueOf(finishedAt),
            taskId
        );
    }

    private void updateTaskFailure(Long taskId, String errorMessage, LocalDateTime finishedAt) {
        jdbcTemplate.update(
            """
            UPDATE import_task
               SET status = ?, total_rows = 0, success_rows = 0, failed_rows = 0,
                   message = ?, finished_at = ?
             WHERE id = ?
            """,
            STATUS_FAILED,
            truncate(Objects.requireNonNullElse(errorMessage, "导入失败，请检查后端日志"), 500),
            Timestamp.valueOf(finishedAt),
            taskId
        );
    }

    private int queryNextMetricOrder() {
        Integer metricOrder = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(metric_order), 0) + 1 FROM dim_process_metric", Integer.class);
        return metricOrder == null ? 1 : metricOrder;
    }

    private String normalizeValidity(String validityLabel) {
        return validityLabel == null ? "" : validityLabel.trim();
    }

    private String summarizeBatchNos(Set<String> batchNos) {
        if (batchNos.isEmpty()) {
            return "";
        }
        if (batchNos.size() == 1) {
            return batchNos.iterator().next();
        }
        List<String> values = new ArrayList<>(batchNos);
        int previewSize = Math.min(values.size(), 3);
        return String.join(", ", values.subList(0, previewSize)) + " 等 " + values.size() + " 个批次";
    }

    private String buildMetricCode(String processCode, String metricName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest((processCode + "::" + metricName).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("M");
            for (int index = 0; index < 15; index++) {
                builder.append(String.format(Locale.ROOT, "%02X", bytes[index]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException("生成测点编码失败: " + exception.getMessage());
        }
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal variance(List<BigDecimal> values, BigDecimal avgValue) {
        if (values.isEmpty() || avgValue == null) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            BigDecimal diff = value.subtract(avgValue);
            sum = sum.add(diff.multiply(diff));
        }
        return sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal sqrt(BigDecimal value) {
        if (value == null) {
            return null;
        }
        double sqrtValue = Math.sqrt(value.doubleValue());
        return BigDecimal.valueOf(sqrtValue).setScale(6, RoundingMode.HALF_UP);
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().format(TIME_FORMATTER);
    }

    private String extractFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return Objects.requireNonNullElse(fileName, "");
        }
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private long nextId(String tableName) {
        Long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM " + tableName, Long.class);
        return id == null ? 1L : id;
    }

    private record BatchMetricKey(String batchNo, String metricCode, String metricName, String importRunId) {
    }

    private record ImportSummary(
        String generatedBatchCode,
        String status,
        int totalRows,
        int successRows,
        int failedRows,
        String message
    ) {
    }

    private record NormalizedMetricRecord(
        String importRunId,
        int rowNo,
        LocalDateTime processTime,
        String batchNo,
        String metricCode,
        String metricName,
        BigDecimal metricValue,
        String validityLabel,
        String rawTextValue,
        boolean success,
        String errorMessage
    ) {
        private static NormalizedMetricRecord success(String importRunId, int rowNo, LocalDateTime processTime,
                                                      String batchNo, String metricCode, String metricName,
                                                      BigDecimal metricValue, String validityLabel, String rawTextValue) {
            return new NormalizedMetricRecord(
                importRunId, rowNo, processTime, batchNo, metricCode, metricName,
                metricValue, validityLabel, rawTextValue, true, null
            );
        }

        private static NormalizedMetricRecord failed(String importRunId, int rowNo, LocalDateTime processTime,
                                                     String batchNo, String metricCode, String metricName,
                                                     String validityLabel, String rawTextValue, String errorMessage) {
            return new NormalizedMetricRecord(
                importRunId, rowNo, processTime, batchNo, metricCode, metricName,
                null, validityLabel, rawTextValue, false, errorMessage
            );
        }
    }
}
