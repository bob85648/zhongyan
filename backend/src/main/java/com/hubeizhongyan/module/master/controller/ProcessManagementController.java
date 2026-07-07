/*
 * 文件名称：ProcessManagementController
 * 文件说明：工序管理控制器，负责对外暴露工序列表与工序详情接口，
 *          支撑前端工序管理模块加载正式库工序主数据。
 * 主要职责：
 * 1. 提供工序列表接口。
 * 2. 提供工序详情接口。
 * 开发者：czd
 */
package com.hubeizhongyan.module.master.controller;

import com.hubeizhongyan.common.domain.ApiResponse;
import com.hubeizhongyan.module.master.dto.ProcessDetailResponse;
import com.hubeizhongyan.module.master.dto.ProcessSummaryResponse;
import com.hubeizhongyan.module.master.service.ProcessManagementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/process-management")
@RequiredArgsConstructor
public class ProcessManagementController {

    private final ProcessManagementService processManagementService;

    @GetMapping
    public ApiResponse<List<ProcessSummaryResponse>> listProcesses() {
        return ApiResponse.success(processManagementService.listProcesses());
    }

    @GetMapping("/{processId}")
    public ApiResponse<ProcessDetailResponse> getProcessDetail(@PathVariable Long processId) {
        return ApiResponse.success(processManagementService.getProcessDetail(processId));
    }
}
