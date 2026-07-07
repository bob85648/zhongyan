/*
 * 文件名称：DemoDataInitializer
 * 文件说明：系统启动演示数据初始化器，用于首次启动时写入最小可运行的工序、变量和批次历史数据。
 * 主要职责：
 * 1. 判断演示库是否已初始化。
 * 2. 初始化工序与变量主数据。
 * 3. 初始化具备不同质量等级的批次分析数据。
 * 开发者：czd
 */
package com.hubeizhongyan.module.demo.support;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DemoBatchSeedService demoBatchSeedService;

    @Override
    public void run(String... args) {
        Integer processCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_info", Integer.class);
        if (processCount != null && processCount > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.of(2026, 5, 14, 8, 0);

        demoBatchSeedService.insertProcess(1L, "PROC_DRY", "叶丝干燥", "历史批次分析演示工序", now);
        demoBatchSeedService.insertProcess(2L, "PROC_FLAVOR", "叶丝加香", "历史批次分析演示工序", now);

        demoBatchSeedService.insertVariable(101L, 1L, "TEMP", "出口温度", "℃", "温度", 95, 130, now);
        demoBatchSeedService.insertVariable(102L, 1L, "MOISTURE", "出口水分", "%", "水分", 9, 16, now);
        demoBatchSeedService.insertVariable(201L, 2L, "FLOW", "香精流量", "L/h", "流量", 35, 55, now);
        demoBatchSeedService.insertVariable(202L, 2L, "PRESSURE", "喷射压力", "kPa", "压力", 90, 140, now);

        // 初始化不同质量等级的批次样本，确保质量分析页面具备清晰的分层展示效果。
        demoBatchSeedService.seedBatch(1001L, 1L, "DRY-20260514-A", "A", now.minusDays(4), List.of(
            new DemoBatchSeedService.VariableSeed(101L, 109.8, 1.8, 11),
            new DemoBatchSeedService.VariableSeed(102L, 12.9, 0.36, 7)
        ));
        demoBatchSeedService.seedBatch(1002L, 1L, "DRY-20260514-B", "B", now.minusDays(3), List.of(
            new DemoBatchSeedService.VariableSeed(101L, 114.2, 2.6, 15),
            new DemoBatchSeedService.VariableSeed(102L, 12.2, 0.62, 9)
        ));
        demoBatchSeedService.seedBatch(1003L, 1L, "DRY-20260514-C", "C", now.minusDays(2), List.of(
            new DemoBatchSeedService.VariableSeed(101L, 120.4, 4.2, 10),
            new DemoBatchSeedService.VariableSeed(102L, 11.4, 1.05, 8)
        ));
        demoBatchSeedService.seedBatch(2001L, 2L, "FLAVOR-20260514-A", "A", now.minusDays(3), List.of(
            new DemoBatchSeedService.VariableSeed(201L, 43.7, 1.2, 8),
            new DemoBatchSeedService.VariableSeed(202L, 112.5, 2.4, 6)
        ));
        demoBatchSeedService.seedBatch(2002L, 2L, "FLAVOR-20260514-B", "B", now.minusDays(2), List.of(
            new DemoBatchSeedService.VariableSeed(201L, 46.1, 2.0, 14),
            new DemoBatchSeedService.VariableSeed(202L, 118.5, 4.1, 10)
        ));
        demoBatchSeedService.seedBatch(2003L, 2L, "FLAVOR-20260514-C", "C", now.minusDays(1), List.of(
            new DemoBatchSeedService.VariableSeed(201L, 49.2, 3.1, 12),
            new DemoBatchSeedService.VariableSeed(202L, 126.4, 5.3, 9)
        ));
    }
}
