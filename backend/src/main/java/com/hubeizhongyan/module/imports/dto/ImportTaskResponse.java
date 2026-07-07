package com.hubeizhongyan.module.imports.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImportTaskResponse {

    private Long id;
    private String processName;
    private String fileName;
    private String generatedBatchCode;
    private String status;
    private Integer totalRows;
    private Integer successRows;
    private Integer failedRows;
    private String message;
    private String createdAt;
    private String finishedAt;
}
