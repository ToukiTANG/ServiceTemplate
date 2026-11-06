import { http } from './request'
import type { DemoItem, DemoItemPayload, DemoItemQuery, PageResult } from '@/types'

/**
 * 示例模块的接口封装。
 *
 * <p>这个文件演示了 api 层的写法：一个函数对应一个后端接口，
 * 函数名用「动词 + 资源」，返回值泛型直接声明成后端 data 的类型。
 * 因为 request.ts 的响应拦截器已经把 { code, message, data } 拆开，
 * 所以这里的返回类型就是 data 本身，业务代码不需要再判 code。
 *
 * <p>删除示例模块时，这个文件也可以一并删掉。
 */

/** 分页查询 */
export function fetchItemPage(query: DemoItemQuery): Promise<PageResult<DemoItem>> {
  return http.get<PageResult<DemoItem>>('/admin/items', { ...query })
}

/** 查询详情 */
export function fetchItem(id: string): Promise<DemoItem> {
  return http.get<DemoItem>(`/admin/items/${id}`)
}

/** 新增 */
export function createItem(payload: DemoItemPayload): Promise<string> {
  return http.post<string>('/admin/items', payload)
}

/** 修改 */
export function updateItem(id: string, payload: DemoItemPayload): Promise<void> {
  return http.put<void>(`/admin/items/${id}`, payload)
}

/** 删除（物理删除，不可恢复） */
export function deleteItem(id: string): Promise<void> {
  return http.delete<void>(`/admin/items/${id}`)
}

/**
 * 启用 / 停用。
 *
 * 注意 status 走的是 query 参数而不是请求体，
 * 与后端 `PATCH /api/admin/items/{id}/status?status=` 的签名对应。
 */
export function changeItemStatus(id: string, status: number): Promise<void> {
  return http.patch<void>(`/admin/items/${id}/status`, undefined, { status })
}
