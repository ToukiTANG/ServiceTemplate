<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { Grid } from '@element-plus/icons-vue'

/**
 * 展示端布局：顶部导航 + 内容区。
 *
 * 没有任何登录逻辑，访客直接可看。
 * 右上角留一个「管理端」入口，登录后才有意义。
 *
 * 手机上的调整（在 style 里用媒体查询处理，不需要 JS 判断）：
 * - 顶栏高度和左右内边距收窄
 * - 内容区左右留白从 24px 减到 16px
 * - 标题字号下调一档
 */
</script>

<template>
  <div class="portal-layout">
    <header class="portal-header">
      <div class="header-inner">
        <RouterLink to="/" class="brand">
          <el-icon :size="20"><Grid /></el-icon>
          <span>ServiceTemplate</span>
        </RouterLink>

        <nav class="portal-nav">
          <RouterLink to="/admin/login" class="nav-link">管理端</RouterLink>
        </nav>
      </div>
    </header>

    <main class="portal-main">
      <RouterView v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </RouterView>
    </main>

    <footer class="portal-footer">
      <span>ServiceTemplate · Java + Vue 服务脚手架</span>
    </footer>
  </div>
</template>

<style scoped>
.portal-layout {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.portal-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--app-border);
}

.header-inner {
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 600;
  color: var(--app-text);
}

.portal-nav {
  display: flex;
  gap: 20px;
}

.nav-link {
  font-size: 14px;
  color: var(--app-text-secondary);
  transition: color 0.2s;
}

.nav-link:hover {
  color: var(--app-primary);
}

.portal-main {
  flex: 1;
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 28px 24px 48px;
}

.portal-footer {
  padding: 20px 24px 32px;
  text-align: center;
  font-size: 12px;
  color: var(--app-text-secondary);
}

/* 手机：收窄留白，顶栏压缩到 54px，避免占掉过多首屏高度 */
@media (max-width: 767px) {
  .header-inner {
    height: 54px;
    padding: 0 16px;
  }

  .brand {
    font-size: 16px;
  }

  .portal-main {
    padding: 20px 16px 36px;
  }

  .portal-footer {
    padding: 16px 16px 24px;
  }
}
</style>
