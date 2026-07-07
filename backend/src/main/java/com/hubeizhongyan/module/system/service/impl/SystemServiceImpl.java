/*
 * 文件名称：SystemServiceImpl
 * 文件说明：系统信息服务实现，负责向前端返回系统名称、版本、运行环境和系统定位说明。
 * 主要职责：
 * 1. 读取系统基础配置。
 * 2. 返回首页所需的系统说明信息。
 * 开发者：czd
 */
package com.hubeizhongyan.module.system.service.impl;

import com.hubeizhongyan.module.system.dto.SystemInfoResponse;
import com.hubeizhongyan.module.system.service.SystemService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SystemServiceImpl implements SystemService {

    @Value("${app.system.name}")
    private String systemName;

    @Value("${app.system.version}")
    private String systemVersion;

    @Value("${spring.profiles.active:postgres}")
    private String environment;

    @Override
    public SystemInfoResponse getSystemInfo() {
        return SystemInfoResponse.builder()
            .systemName(systemName)
            .systemVersion(systemVersion)
            .environment(environment)
            .description("面向烟草制造历史工序指标数据分析与展示的企业级开发框架")
            .build();
    }
}
