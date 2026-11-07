import { http } from './request'
import type {
  TransferAdminItem,
  TransferInfo,
  TransferQuery,
  UploadCompleteResult,
  UploadInitResult
} from '@/types/transfer'
import type { PageResult } from '@/types'

/**
 * 文件传输接口封装。
 *
 * <p>分成两组：
 * <ul>
 *   <li>上传相关：需要登录（后端拦截器保护 {@code /api/transfer/upload/**}）</li>
 *   <li>查询与下载：公开，凭提取码即可</li>
 * </ul>
 */

/** 初始化上传：报文件信息，拿 uploadId 与已传分片 */
export function initUpload(payload: {
  fileName: string
  fileSize: number
  fileHash?: string
  contentType?: string
  expireHours?: number
}): Promise<UploadInitResult> {
  return http.post<UploadInitResult>('/transfer/upload/init', payload)
}

/**
 * 上传一个分片。
 *
 * <p>用 FormData 提交，字段名必须是 {@code file}（与后端 {@code @RequestParam("file")} 对应）。
 *
 * @param uploadId   上传会话 ID
 * @param index      分片序号，从 0 开始
 * @param chunk      分片内容
 * @param onProgress 单片上传进度（0-100）
 */
export function uploadChunk(
  uploadId: string,
  index: number,
  chunk: Blob,
  onProgress?: (percent: number) => void
): Promise<void> {
  const form = new FormData()
  // 第三个参数指定文件名，某些后端要求必须有 filename 才认这是一个文件字段
  form.append('file', chunk, `chunk-${index}`)
  return http.postForm<void>(`/transfer/upload/chunk?uploadId=${uploadId}&index=${index}`, form, onProgress)
}

/** 完成上传：合并分片，拿提取码 */
export function completeUpload(uploadId: string): Promise<UploadCompleteResult> {
  return http.post<UploadCompleteResult>(`/transfer/upload/complete/${uploadId}`)
}

/** 查询文件信息（公开） */
export function fetchTransferInfo(code: string): Promise<TransferInfo> {
  return http.get<TransferInfo>(`/transfer/info/${code}`)
}

/**
 * 下载地址。
 *
 * <p>这里不发起请求，只拼 URL——下载要让浏览器自己处理（弹出保存框、走下载管理器），
 * 用 axios 拉成 blob 再存盘会多占一倍内存，几百 MB 的文件容易把页面搞崩。
 *
 * @param code 提取码
 */
export function buildDownloadUrl(code: string): string {
  return `/api/transfer/download/${code}`
}

/** 管理端：分页查询传输记录 */
export function fetchTransferPage(query: TransferQuery): Promise<PageResult<TransferAdminItem>> {
  return http.get<PageResult<TransferAdminItem>>('/admin/transfers', { ...query })
}

/** 管理端：删除传输记录（同时删磁盘文件） */
export function deleteTransfer(id: string): Promise<void> {
  return http.delete<void>(`/admin/transfers/${id}`)
}
