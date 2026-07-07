/*
 * 文件名称：VariableManagementController
 * 文件说明：变量管理控制器，负责对外暴露变量列表与变量详情接口，
 *          支撑前端变量管理模块按工序查询正式库指标主数据。
 * 主要职责：
 * 1. 提供变量列表查询接口。
 * 2. 提供变量详情查询接口。
 * 开发者：czd
 */
package com.hubeizhongyan.module.master.controller;

import com.hubeizhongyan.common.domain.ApiResponse;
import com.hubeizhongyan.common.domain.PageResponse;
import com.hubeizhongyan.module.master.dto.VariableDetailResponse;
import com.hubeizhongyan.module.master.dto.VariableSummaryResponse;
import com.hubeizhongyan.module.master.service.VariableManagementService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/variable-management")
@RequiredArgsConstructor
public class VariableManagementController {

    private final VariableManagementService variableManagementService;

    @GetMapping
    public ApiResponse<PageResponse<VariableSummaryResponse>> listVariables(
        @RequestParam Long processId,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "10") long pageSize,
        @RequestParam(defaultValue = "metricOrder") String sortField,
        @RequestParam(defaultValue = "asc") String sortOrder
    ) {
        return ApiResponse.success(
            variableManagementService.listVariables(processId, keyword, pageNo, pageSize, sortField, sortOrder)
        );
    }

    @GetMapping("/{metricCode}")
    public ApiResponse<VariableDetailResponse> getVariableDetail(
        @RequestParam Long processId,
        @PathVariable String metricCode
    ) {
        return ApiResponse.success(variableManagementService.getVariableDetail(processId, metricCode));
    }

    /**
     * 变量主数据导出接口统一输出 CSV，便于业务人员直接在 Excel 中查看和二次分析。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportVariables(
        @RequestParam Long processId,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "metricOrder") String sortField,
        @RequestParam(defaultValue = "asc") String sortOrder
    ) {
        byte[] content = variableManagementService.exportVariables(processId, keyword, sortField, sortOrder);
        String fileName = "variable-management.csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(content);
    }
}
