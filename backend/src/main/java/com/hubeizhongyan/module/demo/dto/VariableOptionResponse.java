/*
 * 文件名称：VariableOptionResponse
 * 文件说明：变量下拉选项响应对象，基于正式库指标字典表返回指标编码、名称和展示单位。
 * 主要职责：
 * 1. 承载指标业务主键 metricCode。
 * 2. 承载变量名称与展示单位。
 * 开发者：czd
 */
package com.hubeizhongyan.module.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VariableOptionResponse {

    private String id;
    private String variableName;
    private String unit;
}
