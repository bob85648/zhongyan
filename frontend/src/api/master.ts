/**
 * 文件名称：master
 * 文件说明：主数据管理接口封装文件，负责统一处理工序管理与数据类别管理相关的前端请求，
 * 为管理页面提供清晰稳定的 API 调用入口。
 * 主要职责：
 * 1. 封装工序管理查询接口。
 * 2. 封装数据类别管理查询接口。
 * 3. 定义主数据管理模块所需的前端类型。
 * 开发者：czd
 */
import http from './http'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResponse<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

export interface ProcessSummary {
  processId: number
  processCode: string
  processName: string
  status: string
  variableCount: number
  batchCount: number
  dataPointCount: number
  lastCollectTime: string
}

export interface ProcessDetail extends ProcessSummary {
  sourceFileCount: number
  firstCollectTime: string
  description: string
}

export interface VariableSummary {
  metricCode: string
  metricName: string
  sourceColumnName: string
  metricOrder: number
  batchCount: number
  dataPointCount: number
  lastCollectTime: string
  status: string
}

export interface VariableDetail extends VariableSummary {
  processName: string
  averageValue: number | null
  averageStdValue: number | null
  minValue: number | null
  maxValue: number | null
  firstCollectTime: string
}

export function getProcessManagementList() {
  return http.get<never, ApiResponse<ProcessSummary[]>>('/process-management')
}

export function getProcessManagementDetail(processId: number) {
  return http.get<never, ApiResponse<ProcessDetail>>(`/process-management/${processId}`)
}

export function getVariableManagementList(
  processId: number,
  keyword?: string,
  pageNo = 1,
  pageSize = 10,
  sortField = 'metricOrder',
  sortOrder = 'asc',
) {
  return http.get<never, ApiResponse<PageResponse<VariableSummary>>>('/variable-management', {
    params: { processId, keyword, pageNo, pageSize, sortField, sortOrder },
  })
}

export function getVariableManagementDetail(processId: number, metricCode: string) {
  return http.get<never, ApiResponse<VariableDetail>>(`/variable-management/${metricCode}`, {
    params: { processId },
  })
}

/**
 * 数据类别导出接口统一返回 Blob，便于前端直接触发浏览器下载。
 */
export function exportVariableManagementList(
  processId: number,
  keyword?: string,
  sortField = 'metricOrder',
  sortOrder = 'asc',
) {
  return http.get<never, Blob>('/variable-management/export', {
    params: { processId, keyword, sortField, sortOrder },
    responseType: 'blob',
  })
}
