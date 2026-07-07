/*
 * 文件名称：DemoController
 * 文件说明：历史分析查询控制器，对外提供首页概览、筛选项、趋势图、对比图、统计表和质量分析接口。
 * 主要职责：
 * 1. 暴露历史分析核心查询接口。
 * 2. 统一承接前端页面的正式库查询请求。
 * 开发者：czd
 */
package com.hubeizhongyan.module.demo.controller;

import com.hubeizhongyan.common.domain.ApiResponse;
import com.hubeizhongyan.module.demo.dto.BatchDetailResponse;
import com.hubeizhongyan.module.demo.dto.BatchOptionResponse;
import com.hubeizhongyan.module.demo.dto.BatchStatisticResponse;
import com.hubeizhongyan.module.demo.dto.ComparisonPointResponse;
import com.hubeizhongyan.module.demo.dto.DemoOverviewResponse;
import com.hubeizhongyan.module.demo.dto.ProcessOptionResponse;
import com.hubeizhongyan.module.demo.dto.QualityAnalysisResponse;
import com.hubeizhongyan.module.demo.dto.TrendPointResponse;
import com.hubeizhongyan.module.demo.dto.VariableOptionResponse;
import com.hubeizhongyan.module.demo.service.DemoDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoDataService demoDataService;

    // 首页概览只聚合最核心的展示指标，服务首页首屏快速渲染。
    @GetMapping("/overview")
    public ApiResponse<DemoOverviewResponse> overview() {
        return ApiResponse.success(demoDataService.getOverview());
    }

    // 工序下拉接口当前面向正式库返回可分析工序列表。
    @GetMapping("/processes")
    public ApiResponse<List<ProcessOptionResponse>> processes() {
        return ApiResponse.success(demoDataService.listProcesses());
    }

    // 指标列表依赖工序上下文，避免前端自行拼接无关指标。
    @GetMapping("/variables")
    public ApiResponse<List<VariableOptionResponse>> variables(
        @RequestParam Long processId,
        @RequestParam(required = false) String batchId
    ) {
        return ApiResponse.success(demoDataService.listVariables(processId, batchId));
    }

    // 批次列表按工序范围查询，向页面返回批次业务主键和质量分层标签。
    @GetMapping("/batches")
    public ApiResponse<List<BatchOptionResponse>> batches(@RequestParam Long processId) {
        return ApiResponse.success(demoDataService.listBatches(processId));
    }

    // 批次详情摘要接口基于正式库 batch_no 聚合基础信息和异常统计。
    @GetMapping("/batch-detail")
    public ApiResponse<BatchDetailResponse> batchDetail(@RequestParam Long processId, @RequestParam String batchId) {
        return ApiResponse.success(demoDataService.getBatchDetail(processId, batchId));
    }

    // 趋势图接口直接返回单批次单指标的时序点集合。
    @GetMapping("/trend")
    public ApiResponse<List<TrendPointResponse>> trend(
        @RequestParam Long processId,
        @RequestParam String batchId,
        @RequestParam String variableId
    ) {
        return ApiResponse.success(demoDataService.getTrend(processId, batchId, variableId));
    }

    // 多批次对比接口聚焦“工序 + 指标”维度的批次级统计结果。
    @GetMapping("/comparison")
    public ApiResponse<List<ComparisonPointResponse>> comparison(@RequestParam Long processId, @RequestParam String variableId) {
        return ApiResponse.success(demoDataService.getComparison(processId, variableId));
    }

    // 批次统计表服务首页表格展示，也作为后续导出能力的数据基础。
    @GetMapping("/batch-statistics")
    public ApiResponse<List<BatchStatisticResponse>> batchStatistics(@RequestParam Long processId) {
        return ApiResponse.success(demoDataService.getBatchStatistics(processId));
    }

    // 质量分析接口按“工序 + 指标”聚合批次统计结果，服务质量分层图表和明细展示。
    @GetMapping("/quality-analysis")
    public ApiResponse<QualityAnalysisResponse> qualityAnalysis(@RequestParam Long processId, @RequestParam String variableId) {
        return ApiResponse.success(demoDataService.getQualityAnalysis(processId, variableId));
    }
}
