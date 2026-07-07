/*
 * 文件名称：VariableSummaryResponse
 * 文件说明：变量管理列表响应对象，负责返回正式库指标的列表基础信息和覆盖统计，
 *          支撑变量管理页的查询、筛选、排序和总览展示。
 * 主要职责：
 * 1. 承载变量列表页主要展示字段。
 * 2. 承载变量覆盖批次、数据点和最近采集时间等统计。
 * 3. 为前端表格查询和排序提供统一结构。
 * 开发者：czd
 */
package com.hubeizhongyan.module.master.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VariableSummaryResponse {

    private String metricCode;
    private String metricName;
    private String sourceColumnName;
    private Integer metricOrder;
    private Integer batchCount;
    private Integer dataPointCount;
    private String lastCollectTime;
    private String status;
}
