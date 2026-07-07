/*
 * 文件名称：ProcessManagementService
 * 文件说明：工序管理服务接口，负责定义工序主数据管理页所需的列表与详情查询能力，
 *          统一工序管理模块的服务入口。
 * 主要职责：
 * 1. 提供工序列表查询能力。
 * 2. 提供工序详情查询能力。
 * 开发者：czd
 */
package com.hubeizhongyan.module.master.service;

import com.hubeizhongyan.module.master.dto.ProcessDetailResponse;
import com.hubeizhongyan.module.master.dto.ProcessSummaryResponse;
import java.util.List;

public interface ProcessManagementService {

    List<ProcessSummaryResponse> listProcesses();

    ProcessDetailResponse getProcessDetail(Long processId);
}
