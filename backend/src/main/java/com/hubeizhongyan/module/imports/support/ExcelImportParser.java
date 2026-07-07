/*
 * 文件说明：ExcelImportParser
 * 文件业务说明：Excel 导入解析器，负责识别烟草工序宽表 Excel 的表头结构，将“时间 + 测点值 + 有效性 + 批次”
 *          的三列组模型转换成标准化长表记录，供导入服务继续完成暂存、清洗、正式入库和统计计算。
 * 业务职责：
 * 1. 读取工作簿首个工作表并解析表头。
 * 2. 识别测点值列、有效性列、批次列之间的对应关系。
 * 3. 将每一行时序数据拆解为多条标准测点记录。
 * 4. 保留原始文本值，便于后续失败定位与数据追溯。
 * 开发者：czd
 */
package com.hubeizhongyan.module.imports.support;

import com.hubeizhongyan.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ExcelImportParser {

    private static final String TIME_HEADER = "时间";
    private static final String VALIDITY_SUFFIX = "-有效性";
    private static final String BATCH_SUFFIX = "-批次";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataFormatter dataFormatter = new DataFormatter(Locale.US);

    public ParsedWorkbook parse(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException("导入文件不包含可读取的工作表");
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BusinessException("导入文件缺少表头，无法识别数据模板");
            }

            List<MetricColumnGroup> metricGroups = parseMetricGroups(headerRow);
            if (metricGroups.isEmpty()) {
                throw new BusinessException("未识别到测点列，请检查 Excel 模板是否符合要求");
            }

            List<ParsedMetricRecord> records = new ArrayList<>();
            int dataRowCount = 0;
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                dataRowCount++;
                LocalDateTime processTime = parseProcessTime(row.getCell(0), rowIndex + 1);
                for (MetricColumnGroup metricGroup : metricGroups) {
                    String rawValue = getCellText(row.getCell(metricGroup.valueColumnIndex()));
                    String validityLabel = getCellText(row.getCell(metricGroup.validityColumnIndex()));
                    String batchNo = getCellText(row.getCell(metricGroup.batchColumnIndex()));
                    if (rawValue.isBlank() && validityLabel.isBlank() && batchNo.isBlank()) {
                        continue;
                    }
                    records.add(new ParsedMetricRecord(
                        rowIndex + 1,
                        processTime,
                        batchNo,
                        metricGroup.metricName(),
                        rawValue,
                        validityLabel
                    ));
                }
            }
            return new ParsedWorkbook(sheet.getSheetName(), dataRowCount, records);
        } catch (IOException exception) {
            throw new BusinessException("读取导入文件失败: " + exception.getMessage());
        }
    }

    private List<MetricColumnGroup> parseMetricGroups(Row headerRow) {
        Map<String, MetricColumnBuilder> builders = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String headerText = getCellText(cell);
            if (headerText.isBlank() || TIME_HEADER.equals(headerText)) {
                continue;
            }

            String metricName = headerText;
            MetricColumnType columnType = MetricColumnType.VALUE;
            if (headerText.endsWith(VALIDITY_SUFFIX)) {
                metricName = headerText.substring(0, headerText.length() - VALIDITY_SUFFIX.length());
                columnType = MetricColumnType.VALIDITY;
            } else if (headerText.endsWith(BATCH_SUFFIX)) {
                metricName = headerText.substring(0, headerText.length() - BATCH_SUFFIX.length());
                columnType = MetricColumnType.BATCH;
            }

            final String currentMetricName = metricName;
            MetricColumnBuilder builder = builders.computeIfAbsent(
                currentMetricName,
                key -> new MetricColumnBuilder(currentMetricName)
            );
            builder.setColumnIndex(columnType, cell.getColumnIndex());
        }

        List<MetricColumnGroup> groups = new ArrayList<>();
        for (MetricColumnBuilder builder : builders.values()) {
            if (builder.valueColumnIndex == null) {
                continue;
            }
            groups.add(builder.build());
        }
        return groups;
    }

    private LocalDateTime parseProcessTime(Cell cell, int rowNo) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw new BusinessException("第 " + rowNo + " 行时间为空，无法导入");
        }
        CellType cellType = cell.getCellType();
        if (cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }

        String text = getCellText(cell);
        if (text.isBlank()) {
            throw new BusinessException("第 " + rowNo + " 行时间为空，无法导入");
        }
        try {
            return LocalDateTime.parse(text, TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BusinessException("第 " + rowNo + " 行时间格式不正确: " + text);
        }
    }

    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (!getCellText(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getCellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            CellType cachedType = cell.getCachedFormulaResultType();
            if (cachedType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return TIME_FORMATTER.format(
                    cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                );
            }
        }
        return dataFormatter.formatCellValue(cell).trim();
    }

    public BigDecimal parseMetricValue(String rawValue, int rowNo, String metricName) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException("第 " + rowNo + " 行测点“" + metricName + "”的值不是数值: " + rawValue);
        }
    }

    public record ParsedWorkbook(String sheetName, int dataRowCount, List<ParsedMetricRecord> records) {
    }

    public record ParsedMetricRecord(
        int rowNo,
        LocalDateTime processTime,
        String batchNo,
        String metricName,
        String rawValue,
        String validityLabel
    ) {
    }

    private enum MetricColumnType {
        VALUE,
        VALIDITY,
        BATCH
    }

    private static final class MetricColumnBuilder {
        private final String metricName;
        private Integer valueColumnIndex;
        private Integer validityColumnIndex;
        private Integer batchColumnIndex;

        private MetricColumnBuilder(String metricName) {
            this.metricName = metricName;
        }

        private void setColumnIndex(MetricColumnType columnType, int columnIndex) {
            switch (columnType) {
                case VALUE -> this.valueColumnIndex = columnIndex;
                case VALIDITY -> this.validityColumnIndex = columnIndex;
                case BATCH -> this.batchColumnIndex = columnIndex;
            }
        }

        private MetricColumnGroup build() {
            return new MetricColumnGroup(metricName, valueColumnIndex, validityColumnIndex, batchColumnIndex);
        }
    }

    private record MetricColumnGroup(
        String metricName,
        Integer valueColumnIndex,
        Integer validityColumnIndex,
        Integer batchColumnIndex
    ) {
    }
}
