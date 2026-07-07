/**
 * 文件名称：demo
 * 文件说明：历史分析业务接口定义文件，统一维护正式库概览、批次分析、质量分析等页面所需的数据类型与请求方法。
 * 主要职责：
 * 1. 定义历史分析相关前端数据模型。
 * 2. 封装正式库查询接口调用。
 * 3. 为各业务页面提供统一的数据访问入口。
 * 开发者：czd
 */
import http from './http'
import type { ApiResponse } from './system'

export interface DemoOverview {
  processCount: number
  variableCount: number
  batchCount: number
  dataPointCount: number
  processNames: string[]
}

export interface ProcessOption {
  id: number
  processName: string
}

export interface VariableOption {
  id: string
  variableName: string
  unit: string
}

export interface BatchOption {
  id: string
  batchCode: string
  qualityLevel: string
}

export interface BatchDetail {
  batchId: string
  batchCode: string
  processName: string
  startTime: string
  endTime: string
  qualityLevel: string
  dataPointCount: number
  variableCount: number
  missingCount: number
  statOutlierCount: number
  physicalOutlierCount: number
}

export interface TrendPoint {
  collectTime: string
  rawValue: number | null
  cleanValue: number | null
  standardValue: number | null
  missing: boolean
  statOutlier: boolean
  physicalOutlier: boolean
}

export interface ComparisonPoint {
  batchCode: string
  meanValue: number
  stdValue: number
  missingRate: number
}

export interface BatchStatistic {
  batchCode: string
  variableName: string
  meanValue: number
  stdValue: number
  minValue: number
  maxValue: number
  missingRate: number
  statOutlierRate: number
  physicalOutlierRate: number
}

export interface QualityLevelStatistic {
  qualityLevel: string
  batchCount: number
  avgMeanValue: number
  avgStdValue: number
  avgMissingRate: number
  avgStatOutlierRate: number
  avgPhysicalOutlierRate: number
}

export interface QualityBatchStatistic {
  batchId: string
  batchCode: string
  qualityLevel: string
  meanValue: number
  stdValue: number
  missingRate: number
  statOutlierRate: number
  physicalOutlierRate: number
}

export interface QualityAnalysis {
  processId: number
  processName: string
  variableId: string
  variableName: string
  unit: string
  totalBatchCount: number
  totalDataPointCount: number
  avgMeanValue: number
  avgStdValue: number
  avgMissingRate: number
  avgStatOutlierRate: number
  avgPhysicalOutlierRate: number
  qualityLevelStatistics: QualityLevelStatistic[]
  batchStatistics: QualityBatchStatistic[]
}

export function getOverview() {
  return http.get<never, ApiResponse<DemoOverview>>('/demo/overview')
}

export function getProcesses() {
  return http.get<never, ApiResponse<ProcessOption[]>>('/demo/processes')
}

export function getVariables(processId: number, batchId?: string) {
  return http.get<never, ApiResponse<VariableOption[]>>('/demo/variables', {
    params: { processId, batchId },
  })
}

export function getBatches(processId: number) {
  return http.get<never, ApiResponse<BatchOption[]>>('/demo/batches', {
    params: { processId },
  })
}

export function getTrend(processId: number, batchId: string, variableId: string) {
  return http.get<never, ApiResponse<TrendPoint[]>>('/demo/trend', {
    params: { processId, batchId, variableId },
  })
}

export function getBatchDetail(processId: number, batchId: string) {
  return http.get<never, ApiResponse<BatchDetail>>('/demo/batch-detail', {
    params: { processId, batchId },
  })
}

export function getComparison(processId: number, variableId: string) {
  return http.get<never, ApiResponse<ComparisonPoint[]>>('/demo/comparison', {
    params: { processId, variableId },
  })
}

export function getBatchStatistics(processId: number) {
  return http.get<never, ApiResponse<BatchStatistic[]>>('/demo/batch-statistics', {
    params: { processId },
  })
}

export function getQualityAnalysis(processId: number, variableId: string) {
  return http.get<never, ApiResponse<QualityAnalysis>>('/demo/quality-analysis', {
    params: { processId, variableId },
  })
}
