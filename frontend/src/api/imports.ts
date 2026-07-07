import http from './http'
import type { ApiResponse } from './system'

export interface ImportTask {
  id: number
  processName: string
  fileName: string
  generatedBatchCode: string
  status: string
  totalRows: number
  successRows: number
  failedRows: number
  message: string
  createdAt: string
  finishedAt: string
}

export interface UploadImportResult {
  taskId: number
  generatedBatchCode: string
  status: string
  message: string
}

export function getImportTasks() {
  return http.get<never, ApiResponse<ImportTask[]>>('/imports/tasks')
}

export function uploadImport(processId: number, file: File) {
  const formData = new FormData()
  formData.append('processId', String(processId))
  formData.append('file', file)
  return http.post<FormData, ApiResponse<UploadImportResult>>('/imports/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}
