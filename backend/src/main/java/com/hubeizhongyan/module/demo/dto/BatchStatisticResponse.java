package com.hubeizhongyan.module.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchStatisticResponse {

    private String batchCode;
    private String variableName;
    private double meanValue;
    private double stdValue;
    private double minValue;
    private double maxValue;
    private double missingRate;
    private double statOutlierRate;
    private double physicalOutlierRate;
}
