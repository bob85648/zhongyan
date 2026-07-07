package com.hubeizhongyan.module.imports.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadImportResponse {

    private Long taskId;
    private String generatedBatchCode;
    private String status;
    private String message;
}
