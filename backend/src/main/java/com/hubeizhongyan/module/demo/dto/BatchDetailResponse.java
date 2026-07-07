/*
 * 文件名称：BatchDetailResponse
 * 文件说明：批次详情摘要响应对象，用于承载批次分析页顶部摘要所需的正式库聚合信息。
 * 主要职责：
 * 1. 返回批次业务标识与基础信息。
 * 2. 返回数据点数量、变量数量和异常数量统计。
 * 开发者：czd
 */
package com.hubeizhongyan.module.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchDetailResponse {

    private String batchId;
    private String batchCode;
    private String processName;
    private String startTime;
    private String endTime;
    private String qualityLevel;
    private Integer dataPointCount;
    private Integer variableCount;
    private Integer missingCount;
    private Integer statOutlierCount;
    private Integer physicalOutlierCount;
}
