import http from './http'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface SystemInfo {
  systemName: string
  systemVersion: string
  environment: string
  description: string
}

export function getHealth() {
  return http.get<never, ApiResponse<{ status: string }>>('/health')
}

export function getSystemInfo() {
  return http.get<never, ApiResponse<SystemInfo>>('/system/info')
}
