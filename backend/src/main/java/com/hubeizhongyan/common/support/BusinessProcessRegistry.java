/*
 * 文件说明：BusinessProcessRegistry
 * 文件业务说明：业务工序注册表，统一维护系统当前支持的生产工序基础信息，为导入、分析、工序管理、
 *          数据类别管理等模块提供一致的工序定义来源，避免业务代码重复硬编码工序常量。
 * 业务职责：
 * 1. 提供系统内置工序列表。
 * 2. 按工序 ID 查询工序定义。
 * 3. 作为多工序导入与查询过滤的统一配置入口。
 * 开发者：czd
 */
package com.hubeizhongyan.common.support;

import com.hubeizhongyan.common.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BusinessProcessRegistry {

    private static final List<BusinessProcessDefinition> DEFINITIONS = List.of(
        new BusinessProcessDefinition(1L, "PROC_SHEET_DRYING", "薄板烘丝", "薄板烘丝工序历史数据分析"),
        new BusinessProcessDefinition(2L, "PROC_LOOSE_REMOISTENING", "松散回潮", "松散回潮工序历史数据分析"),
        new BusinessProcessDefinition(3L, "PROC_TOBACCO_DRYING", "烟丝干燥", "烟丝干燥工序历史数据分析")
    );

    public List<BusinessProcessDefinition> listAll() {
        return DEFINITIONS;
    }

    public BusinessProcessDefinition getById(Long processId) {
        return DEFINITIONS.stream()
            .filter(definition -> definition.id().equals(processId))
            .findFirst()
            .orElseThrow(() -> new BusinessException("未找到对应的工序定义，请先选择有效工序"));
    }

    public record BusinessProcessDefinition(
        Long id,
        String processCode,
        String processName,
        String description
    ) {
    }
}
