package com.hubeizhongyan.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI historicalAnalysisOpenApi() {
        return new OpenAPI().info(new Info()
            .title("历史传感器数据分析系统 API")
            .description("武汉中烟历史数据分析与展示系统接口文档")
            .version("0.0.1")
            .contact(new Contact().name("hubeizhongyan")));
    }
}
