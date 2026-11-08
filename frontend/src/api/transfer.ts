import { http } from './request'
import type {
  PrepareResult,
  TransferAdminItem,
  TransferInfo,
  TransferQuery,
  UploadCredential,
  UploadedFile
} from '@/types/transfer'

/**
 * 文件上传与分享。
 *
 * <p>上传走「前端直传对象存储」：文件字节不经过我们的服务器。
 * 原因很实际——服务器公网带宽只有几 Mbps，几百 MB 的文件经中转要二十多分钟，
 * 期间带宽被吃满，其他请求全部排队。
 *
 * <p>完整流程：
 * <pre>
 * prepare    → 拿到 uploadId、storageKey、分片大小
 * initiate   → 又拍云分片任务开始，拿到 multi-uuid
 * 逐片 PUT   → 每片 1MB，顺序上传（又拍云协议要求）
 * complete   → 分片任务结束
 * uploadComplete → 标记服务端记录为可用，拿到文件 ID
 * createTransfer → 生成提取码
 * </pre>
 */

// ==================== 底层接口 ====================

/** 登记上传 */
export function prepareUpload(payload: {
  fileName: string
  fileSize: number
  contentType?: string
  fileHash?: string
}): Promise<PrepareResult> {
  return http.post<PrepareResult>('/upload/prepare', payload)
}

/** 获取签名 */
export function getCredential(storageKey: string, contentLength: number): Promise<UploadCredential> {
  return http.post<UploadCredential>('/upload/credential', {
    method: 'PUT',
    storageKey,
    contentLength
  })
}

/**
 * 标记服务端上传完成。
 *
 * <p>服务端会调用又拍云合并分片，所以必须带上 prepare 时拿到的 {@code multiUuid}。
 */
export function completeUpload(uploadId: string, multiUuid?: string): Promise<UploadedFile> {
  const query = multiUuid ? `?multiUuid=${encodeURIComponent(multiUuid)}` : ''
  return http.post<UploadedFile>(`/upload/complete/${uploadId}${query}`, null)
}

/** 创建传输记录，生成提取码 */
export function createTransfer(uploadFileId: string, expireHours?: number): Promise<TransferInfo> {
  return http.post<TransferInfo>('/transfer/create', { uploadFileId, expireHours })
}

/** 查询文件信息（公开） */
export function fetchTransferInfo(code: string): Promise<TransferInfo> {
  return http.get<TransferInfo>(`/transfer/info/${code}`)
}

/**
 * 下载地址。
 *
 * <p>这是**我们自己后端**的地址，访问后返回 302 跳转到对象存储直链。
 * 用跳转而不是直接给直链，是为了能统计下载次数。
 */
export function buildDownloadUrl(code: string): string {
  return `/api/transfer/download/${code}`
}

/** 管理端：分页查询 */
export function fetchTransferPage(query: TransferQuery): Promise<{
  list: TransferAdminItem[]
  total: number
  pageNum: number
  pageSize: number
}> {
  return http.get('/admin/transfers', { ...query })
}

/** 管理端：删除 */
export function deleteTransfer(id: string): Promise<void> {
  return http.delete<void>(`/admin/transfers/${id}`)
}

// ==================== 直传实现 ====================

/** 签名有效期 30 分钟，25 分钟时刷新，留 5 分钟余量 */
const SIGN_TTL_MS = 25 * 60 * 1000

/**
 * 把文件直传又拍云，并生成提取码。
 *
 * @param file         用户选择的文件
 * @param expireHours  有效期（小时）
 * @param onProgress   进度回调，参数 0-100
 * @returns 提取码与文件信息
 */
export async function uploadAndShare(
  file: File,
  expireHours: number,
  onProgress?: (percent: number) => void
): Promise<TransferInfo> {
  // 1. 登记
  const prep = await prepareUpload({
    fileName: file.name,
    fileSize: file.size,
    contentType: file.type || 'application/octet-stream',
    // 弱指纹：不读文件内容，只用于服务端识别"同一个文件"
    fileHash: `k${file.size.toString(16)}${file.lastModified.toString(16)}`
  })

  const { storageKey, uploadBaseUrl, multiUuid, chunkSize } = prep
  const totalChunks = Math.max(1, Math.ceil(file.size / chunkSize))

  // 签名缓存：V4 签名只依赖 method/path/date，同一个 path 下所有分片可以复用
  let credential: UploadCredential | null = null
  let signedAt = 0

  const ensureCredential = async (): Promise<UploadCredential> => {
    if (!credential || Date.now() - signedAt > SIGN_TTL_MS) {
      credential = await getCredential(storageKey, 0)
      signedAt = Date.now()
    }
    return credential
  }

  /** 往又拍云发一次 PUT，失败抛错 */
  const put = async (headers: Record<string, string>, body?: Blob): Promise<Response> => {
    const cred = await ensureCredential()
    const resp = await fetch(uploadBaseUrl + cred.path, {
      method: 'PUT',
      headers: {
        Authorization: cred.authorization,
        // 用 X-Date 而不是 Date：Date 是浏览器的 forbidden header，
        // JS 里设了也会被静默丢掉，又拍云会报 "need date header"。
        // 又拍云的 CORS 允许列表里有 X-Date，用它替代即可。
        'X-Date': cred.date,
        ...headers
      },
      body
    })
    if (!resp.ok) {
      const text = await resp.text().catch(() => '')
      throw new Error(`上传失败（${resp.status}）${text ? '：' + text : ''}`)
    }
    return resp
  }

  // 2. 分片任务已在 prepare 阶段由服务端初始化，这里直接用返回的 uuid。
  //    不在前端发 initiate：那是无 body 的 PUT，浏览器不能设置 Content-Length，
  //    又拍云会返回 404（现象很迷惑，实际是请求头缺失）。

  // 3. 逐片上传（又拍云协议只支持顺序）
  for (let index = 0; index < totalChunks; index++) {
    const start = index * chunkSize
    const end = Math.min(start + chunkSize, file.size)
    const blob = file.slice(start, end)

    await put(
      {
        'x-upyun-multi-stage': 'upload',
        'x-upyun-multi-uuid': multiUuid,
        'x-upyun-part-id': String(index),
        'Content-Type': 'application/octet-stream'
      },
      blob
    )

    onProgress?.(Math.round(((index + 1) / totalChunks) * 100))
  }

  // 4. 结束分片任务由服务端在 complete 时代发，前端不用管

  // 5. 标记服务端记录可用（服务端会调又拍云合并分片），拿到文件 ID
  const uploaded = await completeUpload(prep.uploadId, multiUuid)

  // 6. 生成提取码
  return createTransfer(uploaded.id, expireHours)
}
