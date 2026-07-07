/*
 * 文件名称：ProcessDetailResponse
 * 文件说明：工序管理详情响应对象，负责向前端返回单个工序的基础信息、历史数据规模、
 *          时间范围和来源文件统计，用于工序管理页详情面板展示。
 * 主要职责：
 * 1. 承载工序基础主数据。
 * 2. 承载正式库历史数据规模统计。
 * 3. 承载工序可视化管理所需的详情字段。
 * 开发者：czd
 */
package com.hubeizhongyan.module.master.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessDetailResponse {

    private Long processId;
    private String processCode;
    private String processName;
    private String status;
    private Integer variableCount;
    private Integer batchCount;
    private Integer dataPointCount;
    private Integer sourceFileCount;
    private String firstCollectTime;
    private String lastCollectTime;
    private String description;
}
