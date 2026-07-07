/*
 * 文件名称：QualityAnalysisResponse
 * 文件说明：质量分析页面聚合响应对象，负责统一输出摘要指标、质量等级分层结果以及批次明细数据。
 * 主要职责：
 * 1. 承载质量分析页顶部摘要信息。
 * 2. 承载质量等级分层统计结果。
 * 3. 承载批次级质量明细数据。
 * 开发者：czd
 */
package com.hubeizhongyan.module.demo.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QualityAnalysisResponse {

    private Long processId;
    private String processName;
    private String variableId;
    private String variableName;
    private String unit;
    private int totalBatchCount;
    private int totalDataPointCount;
    private double avgMeanValue;
    private double avgStdValue;
    private double avgMissingRate;
    private double avgStatOutlierRate;
    private double avgPhysicalOutlierRate;
    private List<QualityLevelStatisticResponse> qualityLevelStatistics;
    private List<QualityBatchResponse> batchStatistics;
}
