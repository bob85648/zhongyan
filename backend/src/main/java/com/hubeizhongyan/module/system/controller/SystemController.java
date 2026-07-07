package com.hubeizhongyan.module.system.controller;

import com.hubeizhongyan.common.domain.ApiResponse;
import com.hubeizhongyan.module.system.dto.SystemInfoResponse;
import com.hubeizhongyan.module.system.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    @GetMapping("/info")
    public ApiResponse<SystemInfoResponse> info() {
        return ApiResponse.success(systemService.getSystemInfo());
    }
}
