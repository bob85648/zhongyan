/*
 * 文件名称：DemoDataService
 * 文件说明：历史分析查询服务接口，面向正式 PostgreSQL 业务库提供概览、趋势、对比、统计和质量分析能力。
 * 主要职责：
 * 1. 提供首页概览数据。
 * 2. 提供工序、指标、批次筛选数据。
 * 3. 提供趋势、对比、统计和质量分析数据。
 * 开发者：czd
 */
package com.hubeizhongyan.module.demo.service;

import com.hubeizhongyan.module.demo.dto.BatchDetailResponse;
import com.hubeizhongyan.module.demo.dto.BatchOptionResponse;
import com.hubeizhongyan.module.demo.dto.BatchStatisticResponse;
import com.hubeizhongyan.module.demo.dto.ComparisonPointResponse;
import com.hubeizhongyan.module.demo.dto.DemoOverviewResponse;
import com.hubeizhongyan.module.demo.dto.ProcessOptionResponse;
import com.hubeizhongyan.module.demo.dto.QualityAnalysisResponse;
import com.hubeizhongyan.module.demo.dto.TrendPointResponse;
import com.hubeizhongyan.module.demo.dto.VariableOptionResponse;
import java.util.List;

public interface DemoDataService {

    DemoOverviewResponse getOverview();

    List<ProcessOptionResponse> listProcesses();

    List<VariableOptionResponse> listVariables(Long processId, String batchId);

    List<BatchOptionResponse> listBatches(Long processId);

    BatchDetailResponse getBatchDetail(Long processId, String batchId);

    List<TrendPointResponse> getTrend(Long processId, String batchId, String variableId);

    List<ComparisonPointResponse> getComparison(Long processId, String variableId);

    List<BatchStatisticResponse> getBatchStatistics(Long processId);

    QualityAnalysisResponse getQualityAnalysis(Long processId, String variableId);
}
