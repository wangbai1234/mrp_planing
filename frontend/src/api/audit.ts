import { get } from './client'

export interface AuditLogRecord {
  id: number
  action: string
  resource_type: string
  resource_id: string
  operator_id: number | null
  operator_name: string | null
  operator_avatar_url?: string | null
  created_at: string
  before_value: unknown
  after_value: unknown
}

export interface AuditLogPage {
  items: AuditLogRecord[]
  total: number
  page: number
  pageSize: number
}

export function getAuditLogs(params: Record<string, string | number>): Promise<AuditLogPage> {
  return get<AuditLogPage>(`/audit-logs?${new URLSearchParams(
    Object.entries(params).map(([key, value]) => [key, String(value)])
  )}`)
}
