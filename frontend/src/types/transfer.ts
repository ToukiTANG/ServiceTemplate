// ==================== 文件传输 ====================

/** 上传初始化返回 */
export interface UploadInitResult {
  /** 上传会话 ID，后续分片请求都要带 */
  uploadId: string
  /** 已上传的分片索引（断点续传时非空） */
  uploadedChunks: number[]
  /** 分片大小（字节），由服务端下发 */
  chunkSize: number
  /** 分片总数 */
  totalChunks: number
}

/** 上传完成返回 */
export interface UploadCompleteResult {
  /** 6 位提取码 */
  code: string
  fileName: string
  fileSize: number
  /** 过期时间，格式 yyyy-MM-dd HH:mm:ss */
  expireTime: string
}

/** 提取页看到的文件信息 */
export interface TransferInfo {
  fileName: string
  fileSize: number
  expireTime: string
  downloadCount: number
}

/** 管理端传输记录 */
export interface TransferAdminItem {
  id: string
  fileName: string
  fileSize: number
  code: string
  expireTime: string
  downloadCount: number
  /** 0 上传中 / 1 可用 / 2 已删除 */
  status: number
  createTime: string
}

/** 管理端查询条件 */
export interface TransferQuery {
  pageNum: number
  pageSize: number
  fileName?: string
  status?: number
}
