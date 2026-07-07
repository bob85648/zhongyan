/*
 * 文件名称：BatchOptionResponse
 * 文件说明：批次下拉选项响应对象，基于正式库批次号返回筛选所需的业务标识和质量分层标签。
 * 主要职责：
 * 1. 承载批次业务主键 batchNo。
 * 2. 承载批次显示编码和质量等级。
 * 开发者：czd
 */
package com.hubeizhongyan.module.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchOptionResponse {

    private String id;
    private String batchCode;
    private String qualityLevel;
}
