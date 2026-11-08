/**
 * 文件上传与传输相关的类型。
 *
 * <p>与后端 VO 一一对应。注意 {@code id} 是 string——后端雪花 ID 序列化时
 * 已统一转成字符串，用 number 接收会丢精度。
 */

/** 上传登记结果（对应 PrepareUploadVO） */
export interface PrepareResult {
  /** 上传记录 ID */
  uploadId: string
  /** 后端分配的存储路径，所有分片的请求路径都用它 */
  storageKey: string
  /** 对象存储接口地址，如 https://v0.api.upyun.com */
  uploadBaseUrl: string
  /** 分片任务标识，服务端在 prepare 时已初始化好 */
  multiUuid: string
  /** 分片大小（字节），又拍云固定 1MB */
  chunkSize: number
  /** 单文件大小上限 */
  maxFileSize: number
}

/** 上传签名（对应 UploadCredentialVO） */
export interface UploadCredential {
  /** 请求头 Authorization 的值 */
  authorization: string
  /** 请求头 Date 的值，必须原样带上（它是签名的输入） */
  date: string
  /** 含 bucket 的请求路径 */
  path: string
}

/** 上传完成后的文件信息（对应 UploadFileVO） */
export interface UploadedFile {
  id: string
  fileName: string
  fileSize: number
  storageKey: string
  /** 完整访问地址 */
  url: string
}

/** 传输信息（对应 TransferFileVO） */
export interface TransferInfo {
  /** 提取码 */
  code: string
  fileName: string
  fileSize: number
  /** 对象存储直链 */
  url: string
  expireTime: string
  downloadCount: number
}

/** 管理端传输记录（对应 TransferAdminVO） */
export interface TransferAdminItem {
  id: string
  code: string
  fileName: string
  fileSize: number
  url: string
  expireTime: string
  downloadCount: number
  /** 1 可用 / 2 已失效 */
  status: number
  createTime: string
}

/** 管理端查询条件 */
export interface TransferQuery {
  pageNum: number
  pageSize: number
  status?: number
}
