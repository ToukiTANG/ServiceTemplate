import axios, { type AxiosInstance } from 'axios'
import type { ApiResult } from '@/types'

/** localStorage 里存 token 的键名，统一在这里定义，避免各处写错字符串 */
const TOKEN_KEY = 'template_token'

/** 读取本地 token，没有则返回空串 */
export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

/** 登录成功后写入 token */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 退出登录或 token 失效时清除 */
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * 处理登录态失效。
 *
 * 这里用动态 import 拿 router，而不是在文件顶部 import。
 * 原因：router → store → api → request 已经构成一条链，
 * 如果 request 在顶部再 import router，就形成了循环依赖，
 * 打包时会出现「router 是 undefined」这种只在生产构建才暴露的问题。
 *
 * 动态 import 是运行时求值，此时 router 模块早已初始化完成，安全。
 */
async function handleUnauthorized(): Promise<void> {
  clearToken()
  const { default: router } = await import('@/router')
  const current = router.currentRoute.value
  if (current.path === '/admin/login') {
    return
  }
  await router.replace({
    path: '/admin/login',
    query: { redirect: current.fullPath }
  })
}

/**
 * axios 实例。
 *
 * baseURL 设为 /api：开发期由 vite proxy 转发到 8080，
 * 生产期由 nginx 反代到后端，前端代码两边通吃。
 */
const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截：自动带上 Bearer token
instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：把 { code, message, data } 拆开，业务代码直接拿 data
instance.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>

    // 后端约定 code=0 为成功，此时把 data 直接往下传
    if (body.code === 0) {
      return body.data as never
    }

    // 业务层的 401（比如 token 过期但被后端以 200 包装返回）
    if (body.code === 401) {
      void handleUnauthorized()
      return Promise.reject(new Error(body.message || '登录已失效'))
    }

    // 其余业务错误统一弹提示，调用方一般不需要再处理
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    const status = error?.response?.status

    // HTTP 层的 401：拦截器直接返回的，没有包装成 R
    if (status === 401) {
      void handleUnauthorized()
      return Promise.reject(new Error('登录已失效，请重新登录'))
    }

    // 后端抛异常时 GlobalExceptionHandler 也会用 200 包业务码，
    // 所以能走到这里的多半是网络问题、超时或 5xx
    let message = '网络异常，请稍后重试'
    if (status === 400) message = '请求参数有误'
    else if (status === 403) message = '没有权限'
    else if (status === 404) message = '接口不存在'
    else if (status && status >= 500) message = '服务器开小差了'

    ElMessage.error(message)
    return Promise.reject(error)
  }
)

/**
 * 对 axios 的薄封装，让调用方只关心返回的数据类型。
 *
 * 拦截器已经把 AxiosResponse 换成了 data 本身，
 * 但 TS 类型上 axios 仍然认为是 AxiosResponse，所以这里断言一次。
 * 集中在这一个文件里断言，好过在每个 api 函数里各写一遍。
 */
export const http = {
  get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    return instance.get(url, { params }) as unknown as Promise<T>
  },
  post<T>(url: string, data?: unknown): Promise<T> {
    return instance.post(url, data) as unknown as Promise<T>
  },
  put<T>(url: string, data?: unknown): Promise<T> {
    return instance.put(url, data) as unknown as Promise<T>
  },
  patch<T>(url: string, data?: unknown, params?: Record<string, unknown>): Promise<T> {
    return instance.patch(url, data, { params }) as unknown as Promise<T>
  },
  delete<T>(url: string): Promise<T> {
    return instance.delete(url) as unknown as Promise<T>
  }
}
