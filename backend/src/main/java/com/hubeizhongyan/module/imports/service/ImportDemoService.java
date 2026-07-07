package com.hubeizhongyan.module.imports.service;

import com.hubeizhongyan.module.imports.dto.ImportTaskResponse;
import com.hubeizhongyan.module.imports.dto.UploadImportResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ImportDemoService {

    UploadImportResponse upload(Long processId, MultipartFile file);

    List<ImportTaskResponse> listTasks();
}
