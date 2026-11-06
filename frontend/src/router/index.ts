import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 路由表。
 *
 * 结构上分三块：
 * 1. 展示端：`/` 挂 PortalLayout，子路由才是具体页面
 * 2. 管理端：`/admin/login` 是独立页面（不套布局），`/admin` 挂 AdminLayout
 * 3. 兜底：未匹配的路径全部走 404
 *
 * 所有页面都用动态 import，让 vite 按路由拆包，
 * 首屏只加载展示端需要的代码，管理端的表格、表单等体积较大的组件延后加载。
 *
 * <p><b>新增页面时</b>：在对应父路由的 children 里加一条即可。
 * 站内页面不需要改拦截器，也不需要权限配置——认证只按 `/api/**` 前缀判断。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/PortalLayout.vue'),
      children: [
        {
          path: '',
          name: 'portal-home',
          component: () => import('@/views/portal/HomeView.vue')
        }
      ]
    },
    {
      // 登录页单独一层，不套 AdminLayout：未登录时侧边栏没有意义
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/admin/LoginView.vue')
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        {
          // 管理端首页就是示例列表；有自己的业务时，改成 Dashboard 或直接指向业务首页
          path: '',
          name: 'admin-items',
          component: () => import('@/views/admin/ItemListView.vue')
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue')
    }
  ]
})

/**
 * 全局前置守卫：只保护 /admin/**。
 *
 * 展示端是公开的，不做任何判断；管理端必须登录。
 * 已登录用户访问登录页时直接送回管理端，省一次手动跳转。
 *
 * 注意这里必须在守卫**内部**调用 useUserStore()，
 * 不能在模块顶层调用——那时 pinia 还没安装，会抛错。
 */
router.beforeEach((to) => {
  // 非管理端路由一律放行
  if (!to.path.startsWith('/admin')) {
    return true
  }

  const userStore = useUserStore()

  // 已登录还去登录页，说明是手动改地址或点了返回，直接送去管理端
  if (to.path === '/admin/login') {
    return userStore.isLoggedIn ? { path: '/admin' } : true
  }

  // 未登录访问管理端页面：记住原地址，登录后跳回去
  if (!userStore.isLoggedIn) {
    return {
      path: '/admin/login',
      query: { redirect: to.fullPath }
    }
  }

  return true
})

export default router
