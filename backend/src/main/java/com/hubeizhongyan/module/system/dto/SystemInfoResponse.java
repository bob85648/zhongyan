package com.hubeizhongyan.module.system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemInfoResponse {

    private String systemName;
    private String systemVersion;
    private String environment;
    private String description;
}
