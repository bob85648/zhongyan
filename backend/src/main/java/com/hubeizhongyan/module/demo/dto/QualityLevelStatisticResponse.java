/*
 * 文件名称：QualityLevelStatisticResponse
 * 文件说明：质量等级聚合统计响应对象，用于承载同一变量在不同质量等级下的批次数量与统计表现。
 * 主要职责：
 * 1. 表达质量等级维度的批次数量。
 * 2. 表达质量等级维度的均值、波动和异常率。
 * 开发者：czd
 */
package com.hubeizhongyan.module.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QualityLevelStatisticResponse {

    private String qualityLevel;
    private int batchCount;
    private double avgMeanValue;
    private double avgStdValue;
    private double avgMissingRate;
    private double avgStatOutlierRate;
    private double avgPhysicalOutlierRate;
}
