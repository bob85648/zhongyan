package com.hubeizhongyan.module.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ComparisonPointResponse {

    private String batchCode;
    private double meanValue;
    private double stdValue;
    private double missingRate;
}
