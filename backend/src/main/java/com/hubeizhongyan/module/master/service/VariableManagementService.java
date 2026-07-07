/*
 * 文件名称：VariableManagementService
 * 文件说明：变量管理服务接口，负责定义正式库指标主数据的列表与详情查询能力，
 *          为变量管理页面和后续规则配置扩展提供统一服务入口。
 * 主要职责：
 * 1. 提供变量列表查询能力。
 * 2. 提供变量详情查询能力。
 * 开发者：czd
 */
package com.hubeizhongyan.module.master.service;

import com.hubeizhongyan.common.domain.PageResponse;
import com.hubeizhongyan.module.master.dto.VariableDetailResponse;
import com.hubeizhongyan.module.master.dto.VariableSummaryResponse;

public interface VariableManagementService {

    PageResponse<VariableSummaryResponse> listVariables(
        Long processId,
        String keyword,
        long pageNo,
        long pageSize,
        String sortField,
        String sortOrder
    );

    VariableDetailResponse getVariableDetail(Long processId, String metricCode);

    byte[] exportVariables(Long processId, String keyword, String sortField, String sortOrder);
}
