/**
 * 全局类型定义。
 *
 * 与后端 VO 一一对应。注意 id 是 string 而不是 number：
 * 后端用雪花 ID（19 位 Long），序列化时已统一转成字符串，
 * 前端如果用 number 接收会丢精度（超过 2^53 的部分变成 0）。
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

/** 工具（管理端，字段最全） */
export interface Tool {
  id: string
  name: string
  description: string | null
  icon: string | null
  link: string
  category: string
  sort: number
  /** 1 上架 / 0 下架 */
  status: number
  createTime?: string
  updateTime?: string
}

/** 工具（展示端，只有渲染卡片需要的字段） */
export interface PortalTool {
  id: string
  name: string
  description: string | null
  icon: string | null
  link: string
  category: string
  sort: number
}

/** 管理端列表查询条件 */
export interface ToolQuery {
  pageNum: number
  pageSize: number
  name?: string
  category?: string
  status?: number
}

/** 新增/修改工具的提交体 */
export interface ToolPayload {
  name: string
  description?: string
  icon?: string
  link: string
  category?: string
  sort?: number
  status?: number
}
