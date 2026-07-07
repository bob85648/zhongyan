/*
 * 文件名称：HistoricalAnalysisApplicationTests
 * 文件说明：正式数据库适配后的集成测试，验证概览、批次详情、趋势与质量分析接口是否可稳定返回结果。
 * 主要职责：
 * 1. 校验应用上下文可以启动。
 * 2. 校验首页概览接口可以返回正式结构统计结果。
 * 3. 校验批次详情和趋势接口可以返回核心分析结果。
 * 4. 校验质量分析接口可以返回分层统计结果。
 * 开发者：czd
 */
package com.hubeizhongyan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HistoricalAnalysisApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void demoOverviewShouldReturnFormalStats() throws Exception {
        // 正式库概览至少应返回工序、指标、批次和数据点统计结果。
        mockMvc.perform(get("/api/demo/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.processCount").value(1))
            .andExpect(jsonPath("$.data.variableCount").value(2))
            .andExpect(jsonPath("$.data.batchCount").value(3));
    }

    @Test
    void batchDetailShouldReturnFormalSummary() throws Exception {
        // 批次分析页依赖的批次详情接口必须返回正式库聚合后的摘要信息。
        mockMvc.perform(get("/api/demo/batch-detail").param("batchId", "BATCH-A-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.batchCode").value("BATCH-A-001"))
            .andExpect(jsonPath("$.data.variableCount").value(2));
    }

    @Test
    void trendShouldReturnPointsWithOutlierFlags() throws Exception {
        // 趋势接口需要返回时序点，并能标识统计异常点。
        mockMvc.perform(get("/api/demo/trend")
                .param("batchId", "BATCH-A-001")
                .param("variableId", "P001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(3)))
            .andExpect(jsonPath("$.data[2].statOutlier").value(true));
    }

    @Test
    void qualityAnalysisShouldReturnGroupedData() throws Exception {
        // 质量分析页需要同时拿到质量等级分层结果与批次明细结果。
        mockMvc.perform(get("/api/demo/quality-analysis")
                .param("processId", "1")
                .param("variableId", "P001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.variableId").value("P001"))
            .andExpect(jsonPath("$.data.totalBatchCount").value(greaterThanOrEqualTo(3)))
            .andExpect(jsonPath("$.data.qualityLevelStatistics.length()").value(greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.data.batchStatistics.length()").value(greaterThanOrEqualTo(3)));
    }
}
