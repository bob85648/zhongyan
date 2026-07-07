package com.hubeizhongyan.module.demo.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DemoOverviewResponse {

    private long processCount;
    private long variableCount;
    private long batchCount;
    private long dataPointCount;
    private List<String> processNames;
}
