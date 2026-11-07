/**
 * 全局类型定义。
 *
 * 与后端 VO 一一对应。注意 id 是 string 而不是 number：
 * 后端用雪花 ID（19 位 Long），序列化时已统一转成字符串，
 * 前端如果用 number 接收会丢精度（超过 2^53 的部分变成 0）。
 *
 * 业务类型不要往这里堆。各模块自己建文件（如 {@code types/transfer.ts}），
 * 这样本文件在脚手架分支和业务分支上保持一致，merge 时不会冲突。
 */

/** 后端统一响应体 */
export interface ApiResult<T = unknown> {
  /** 0 表示成功，其余为业务错误码 */
  code: number
  /** 提示信息，失败时可直接展示给用户 */
  message: string
  /** 业务数据 */
  data: T
}

/** 分页结果 */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

/** 当前登录用户 */
export interface UserInfo {
  id: string
  username: string
  nickname: string | null
}

/** 登录返回 */
export interface LoginResult {
  token: string
  user: UserInfo
}
