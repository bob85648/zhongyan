/*
 * 文件名称：QualityBatchResponse
 * 文件说明：质量分析批次明细响应对象，用于展示批次级别的质量等级与统计指标。
 * 主要职责：
 * 1. 承载批次业务标识和基础信息。
 * 2. 承载批次级统计结果，供表格和图表复用。
 * 开发者：czd
 */
package com.hubeizhongyan.module.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QualityBatchResponse {

    private String batchId;
    private String batchCode;
    private String qualityLevel;
    private double meanValue;
    private double stdValue;
    private double missingRate;
    private double statOutlierRate;
    private double physicalOutlierRate;
}
