<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Component } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ArrowDown, Expand, Grid, SwitchButton } from '@element-plus/icons-vue'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/utils/responsive'

/**
 * 管理端布局：左侧固定菜单 + 顶部用户栏 + 内容区。
 *
 * 响应式策略：
 * - 桌面（≥768px）：侧边栏常驻
 * - 手机（<768px）：侧边栏隐藏，改用「汉堡按钮 + 抽屉」，点菜单项后抽屉自动收起
 *
 * <b>菜单由路由的 meta 生成，不写死在这里。</b>
 * 给管理端路由加上 `meta: { menu: true, title, icon, order }`，菜单就会自动出现。
 * 这样本文件在脚手架分支和业务分支上完全一致——加菜单只改各分支自己的 router，
 * merge 时不会冲突。
 */

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 是否为手机尺寸，由媒体查询驱动，CSS 与 JS 共用 767px 这个断点 */
const isMobile = useIsMobile()

/** 站点名，与 PortalLayout 共用同一个环境变量（.env 的 VITE_APP_NAME） */
const appName = import.meta.env.VITE_APP_NAME || 'ServiceTemplate'

/** 移动端抽屉菜单是否展开 */
const drawerVisible = ref(false)

/**
 * 图标名转组件。
 *
 * 路由 meta 里只存字符串（如 'House'），这里查表拿到组件对象。
 * 好处：路由表不需要 import 组件，图标名写错也只是退化成默认图标，不会编译失败。
 */
function iconOf(name?: unknown): Component {
  const icons = ElementPlusIcons as unknown as Record<string, Component>
  return (typeof name === 'string' ? icons[name] : undefined) || ElementPlusIcons.Menu
}

/**
 * 菜单项，从路由表推导。
 *
 * 筛选条件：路径在 /admin 下、且 meta.menu 为 true。
 * 排序用 meta.order，不依赖 getRoutes() 的返回顺序（那个顺序没有保证）。
 */
const menus = computed(() =>
  router
    .getRoutes()
    .filter((r) => r.meta?.menu === true && r.path.startsWith('/admin'))
    .sort((a, b) => Number(a.meta?.order ?? 99) - Number(b.meta?.order ?? 99))
    .map((r) => ({
      path: r.path,
      title: String(r.meta?.title ?? ''),
      icon: iconOf(r.meta?.icon)
    }))
)

/** 当前高亮项跟随路由 */
const activeMenu = computed(() => route.path)

/** 顶部标题也来自路由 meta，进哪个页面显示哪个名字 */
const currentTitle = computed(() => String(route.meta?.title ?? '管理端'))

/**
 * 退出登录。
 *
 * 用 MessageBox 二次确认，避免误点——重新登录要重新输密码，对使用体验影响不小。
 */
async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
  } catch {
    // 点了取消，什么都不做
    return
  }

  userStore.logout()
  await router.replace('/admin/login')
}

/** 移动端点完菜单就收起抽屉，否则会挡住刚打开的页面 */
function handleMenuSelect(): void {
  if (isMobile.value) {
    drawerVisible.value = false
  }
}
</script>

<template>
  <el-container class="admin-layout">
    <!-- 桌面端常驻侧边栏 -->
    <el-aside v-if="!isMobile" width="216px" class="admin-aside">
      <div class="aside-logo">
        <el-icon :size="18"><Grid /></el-icon>
        <span>{{ appName }} 后台</span>
      </div>

      <el-menu :default-active="activeMenu" router class="aside-menu">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>

      <div class="aside-bottom">
        <router-link to="/" class="back-link">← 返回展示页</router-link>
      </div>
    </el-aside>

    <!--
      移动端抽屉菜单。
      append-to-body 让抽屉渲染到 body 下，避免被 el-container 的 flex 布局挤变形。
    -->
    <el-drawer
      v-if="isMobile"
      v-model="drawerVisible"
      direction="ltr"
      size="240px"
      :with-header="false"
      append-to-body
    >
      <div class="drawer-inner">
        <div class="aside-logo">
          <el-icon :size="18"><Grid /></el-icon>
          <span>{{ appName }} 后台</span>
        </div>

        <el-menu :default-active="activeMenu" router class="aside-menu" @select="handleMenuSelect">
          <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.title }}</span>
          </el-menu-item>
        </el-menu>

        <div class="aside-bottom">
          <router-link to="/" class="back-link">← 返回展示页</router-link>
        </div>
      </div>
    </el-drawer>

    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <!-- 手机上才出现，桌面端侧边栏常驻，不需要这个按钮 -->
          <el-button
            v-if="isMobile"
            class="menu-toggle"
            :icon="Expand"
            text
            @click="drawerVisible = true"
          />
          <span class="header-title">{{ currentTitle }}</span>
        </div>

        <el-dropdown trigger="click">
          <span class="user-trigger">
            {{ userStore.displayName }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :icon="SwitchButton" @click="handleLogout">
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="admin-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  height: 100%;
}

.admin-aside {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid var(--app-border);
}

.aside-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 60px;
  padding: 0 20px;
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid var(--app-border);
}

.aside-menu {
  flex: 1;
  border-right: none;
}

.aside-bottom {
  padding: 16px 20px;
  border-top: 1px solid var(--app-border);
}

.back-link {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.back-link:hover {
  color: var(--app-primary);
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  background: #fff;
  border-bottom: 1px solid var(--app-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.menu-toggle {
  padding: 4px;
  font-size: 20px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: var(--app-text);
  cursor: pointer;
  outline: none;
}

.admin-main {
  background: var(--app-bg);
  padding: 20px;
}

/* 抽屉里的内容：撑满高度，底部「返回展示页」压到最后 */
.drawer-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.drawer-inner .aside-menu {
  flex: 1;
  border-right: none;
}

/* 抽屉自带内边距，会和我们自己的 padding 叠加，这里清掉 */
:deep(.el-drawer__body) {
  padding: 0;
}

@media (max-width: 767px) {
  .admin-header {
    height: 54px;
    padding: 0 12px;
  }

  .header-title {
    font-size: 15px;
  }

  .admin-main {
    padding: 12px;
  }

  .aside-logo {
    height: 54px;
    padding: 0 16px;
  }

  .aside-bottom {
    padding: 14px 16px;
  }
}
</style>
