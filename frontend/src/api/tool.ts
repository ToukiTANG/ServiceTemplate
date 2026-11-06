import { http } from './request'
import type { PageResult, PortalTool, Tool, ToolPayload, ToolQuery } from '@/types'

/** 展示端：拿所有上架工具（公开接口，无需 token） */
export function fetchPortalTools(): Promise<PortalTool[]> {
  return http.get<PortalTool[]>('/portal/tools')
}

/** 管理端：分页查询工具 */
export function fetchToolPage(query: ToolQuery): Promise<PageResult<Tool>> {
  return http.get<PageResult<Tool>>('/admin/tools', { ...query })
}

/** 管理端：查单个工具（编辑前回填表单用） */
export function fetchTool(id: string): Promise<Tool> {
  return http.get<Tool>(`/admin/tools/${id}`)
}

/** 管理端：新增 */
export function createTool(payload: ToolPayload): Promise<Tool> {
  return http.post<Tool>('/admin/tools', payload)
}

/** 管理端：修改 */
export function updateTool(id: string, payload: ToolPayload): Promise<Tool> {
  return http.put<Tool>(`/admin/tools/${id}`, payload)
}

/** 管理端：删除（物理删除，不可恢复） */
export function deleteTool(id: string): Promise<void> {
  return http.delete<void>(`/admin/tools/${id}`)
}

/**
 * 管理端：上架 / 下架。
 *
 * 注意 status 走的是 query 参数而不是请求体，
 * 与后端 `PATCH /api/admin/tools/{id}/status?status=` 的签名对应。
 */
export function changeToolStatus(id: string, status: number): Promise<void> {
  return http.patch<void>(`/admin/tools/${id}/status`, undefined, { status })
}
