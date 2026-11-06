import { http } from './request'
import type { LoginResult, UserInfo } from '@/types'

/**
 * 登录。
 *
 * 后端返回 { token, user }，token 由调用方（store）负责持久化，
 * 这里只做一次 HTTP 调用，不碰 localStorage。
 */
export function login(username: string, password: string): Promise<LoginResult> {
  return http.post<LoginResult>('/auth/login', { username, password })
}

/** 获取当前登录用户信息（页面刷新后用它恢复昵称） */
export function fetchMe(): Promise<UserInfo> {
  return http.get<UserInfo>('/auth/me')
}

/** 修改密码，成功后后端不会让旧 token 失效，前端自行决定是否重新登录 */
export function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  return http.put<void>('/auth/password', { oldPassword, newPassword })
}
