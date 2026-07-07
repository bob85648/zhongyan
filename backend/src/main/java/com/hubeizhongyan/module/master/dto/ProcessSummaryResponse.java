/*
 * 文件名称：ProcessSummaryResponse
 * 文件说明：工序管理列表响应对象，负责返回工序主数据总览与核心统计指标，
 *          便于工序管理页进行表格展示和导航定位。
 * 主要职责：
 * 1. 承载工序列表页基础字段。
 * 2. 承载工序数据规模与最近采集时间。
 * 3. 为前端列表排序和概览展示提供统一结构。
 * 开发者：czd
 */
package com.hubeizhongyan.module.master.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessSummaryResponse {

    private Long processId;
    private String processCode;
    private String processName;
    private String status;
    private Integer variableCount;
    private Integer batchCount;
    private Integer dataPointCount;
    private String lastCollectTime;
}
