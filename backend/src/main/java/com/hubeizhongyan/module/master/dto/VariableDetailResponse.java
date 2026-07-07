/*
 * 文件名称：VariableDetailResponse
 * 文件说明：变量管理详情响应对象，负责返回正式库指标的基础定义、数据覆盖情况、
 *          数值分布范围和波动水平，用于变量管理页的详情画像展示。
 * 主要职责：
 * 1. 承载指标基础主数据。
 * 2. 承载指标历史覆盖范围和统计特征。
 * 3. 为后续扩展阈值配置和质量规则预留字段结构。
 * 开发者：czd
 */
package com.hubeizhongyan.module.master.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VariableDetailResponse {

    private String metricCode;
    private String metricName;
    private String sourceColumnName;
    private Integer metricOrder;
    private String processName;
    private Integer batchCount;
    private Integer dataPointCount;
    private Double averageValue;
    private Double averageStdValue;
    private Double minValue;
    private Double maxValue;
    private String firstCollectTime;
    private String lastCollectTime;
    private String status;
}
