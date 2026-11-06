import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserInfo } from '@/types'
import { fetchMe, login as loginApi } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/api/request'

/**
 * 用户状态。
 *
 * 采用 setup 风格的 defineStore：状态、计算属性、方法写在一起，
 * 比 options 风格更接近普通的组合式函数，读起来没有额外的概念。
 *
 * token 以 localStorage 为准（刷新页面后依然存在），
 * user 则是纯内存态，刷新后由路由守卫调用 loadUser 重新拉取。
 */
export const useUserStore = defineStore('user', () => {
  /** token，初始值直接读 localStorage，避免刷新后瞬间变成未登录 */
  const token = ref(getToken())

  /** 当前用户信息，未登录时为 null */
  const user = ref<UserInfo | null>(null)

  /** 是否已登录：只判断 token 是否存在，不做有效性校验 */
  const isLoggedIn = computed(() => Boolean(token.value))

  /** 昵称，没有昵称时退回用户名，再没有就显示「管理员」 */
  const displayName = computed(() => user.value?.nickname || user.value?.username || '管理员')

  /**
   * 登录：拿 token → 存起来 → 记录用户信息。
   * 失败时异常会向上抛，由调用方（登录页）决定怎么提示。
   */
  async function login(username: string, password: string): Promise<void> {
    const result = await loginApi(username, password)
    token.value = result.token
    setToken(result.token)
    user.value = result.user
  }

  /**
   * 拉取当前用户信息。
   *
   * 页面刷新后 user 是空的，但 token 还在，
   * 此时需要用它把昵称补回来。401 会由 request 拦截器统一处理。
   */
  async function loadUser(): Promise<void> {
    if (!token.value) return
    user.value = await fetchMe()
  }

  /** 退出登录：清内存 + 清 localStorage */
  function logout(): void {
    token.value = ''
    user.value = null
    clearToken()
  }

  return { token, user, isLoggedIn, displayName, login, loadUser, logout }
})
