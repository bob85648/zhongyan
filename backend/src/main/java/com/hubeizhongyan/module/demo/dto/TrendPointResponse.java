package com.hubeizhongyan.module.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrendPointResponse {

    private String collectTime;
    private Double rawValue;
    private Double cleanValue;
    private Double standardValue;
    private boolean missing;
    private boolean statOutlier;
    private boolean physicalOutlier;
}
