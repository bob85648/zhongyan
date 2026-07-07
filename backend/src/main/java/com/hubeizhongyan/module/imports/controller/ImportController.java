/*
 * 文件名称：ImportController
 * 文件说明：文件导入演示接口控制器，对外提供上传演示文件和查询导入任务列表能力，
 *          用于打通“上传 -> 任务 -> 批次生成 -> 分析展示”的最小业务流程。
 * 主要职责：
 * 1. 接收上传文件请求。
 * 2. 调用导入服务生成任务和批次数据。
 * 3. 返回导入任务列表供前端展示。
 * 开发者：czd
 */
package com.hubeizhongyan.module.imports.controller;

import com.hubeizhongyan.common.domain.ApiResponse;
import com.hubeizhongyan.module.imports.dto.ImportTaskResponse;
import com.hubeizhongyan.module.imports.dto.UploadImportResponse;
import com.hubeizhongyan.module.imports.service.ImportDemoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportDemoService importDemoService;

    // 文件导入入口由开发者 czd 对接：当前用于演示“上传 -> 生成任务 -> 产出批次”的最小闭环。
    @PostMapping("/upload")
    public ApiResponse<UploadImportResponse> upload(
        @RequestParam Long processId,
        @RequestParam MultipartFile file
    ) {
        return ApiResponse.success(importDemoService.upload(processId, file));
    }

    // 任务列表接口用于给前端展示最近导入记录和生成结果。
    @GetMapping("/tasks")
    public ApiResponse<List<ImportTaskResponse>> tasks() {
        return ApiResponse.success(importDemoService.listTasks());
    }
}
