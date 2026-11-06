<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ArrowRight, Document, Grid, Lock } from '@element-plus/icons-vue'

/**
 * 展示端首页。
 *
 * 脚手架里这只是一个说明页，不承载任何业务：
 * - 告诉使用者这个模板提供了什么
 * - 给出管理端和接口文档的入口
 * - 提醒哪些地方是需要替换的
 *
 * <p><b>改造方向</b>：如果要做成门户/工具导航/产品介绍页，
 * 直接把内容换成自己的即可。PortalLayout 已经提供了顶栏和内容区，
 * 具体页面完全由业务决定。
 */

/** 关键入口，集中配置，方便以后改地址 */
const entries = [
  {
    title: '管理端',
    desc: '登录后可以体验一个完整的增删改查示例',
    to: '/admin',
    icon: Lock
  }
]
</script>

<template>
  <div class="home">
    <section class="hero">
      <h1 class="hero-title">ServiceTemplate</h1>
      <p class="hero-sub">
        一个 Java + Vue 的服务脚手架。已包含登录认证、统一响应、分页、数据库迁移，
        以及一个可以照抄的示例模块。
      </p>

      <div class="entry-list">
        <RouterLink v-for="item in entries" :key="item.to" :to="item.to" class="entry">
          <el-icon :size="20" class="entry-icon"><component :is="item.icon" /></el-icon>
          <div class="entry-body">
            <div class="entry-title">{{ item.title }}</div>
            <div class="entry-desc">{{ item.desc }}</div>
          </div>
          <el-icon class="entry-arrow"><ArrowRight /></el-icon>
        </RouterLink>

        <a
          href="http://localhost:8080/swagger-ui.html"
          target="_blank"
          rel="noopener noreferrer"
          class="entry"
        >
          <el-icon :size="20" class="entry-icon"><Document /></el-icon>
          <div class="entry-body">
            <div class="entry-title">接口文档</div>
            <div class="entry-desc">Swagger UI，登录接口返回的 token 可以直接在页面里调试</div>
          </div>
          <el-icon class="entry-arrow"><ArrowRight /></el-icon>
        </a>
      </div>
    </section>

    <section class="note">
      <h2 class="note-title">
        <el-icon><Grid /></el-icon>
        这个模板包含什么
      </h2>
      <ul class="note-list">
        <li>统一响应体、全局异常、参数校验、分页封装</li>
        <li>JWT 登录、拦截器鉴权、BCrypt 密码</li>
        <li>Flyway 管理表结构，启动自动建表</li>
        <li>Swagger 接口文档，前端 axios 封装与 401 处理</li>
        <li>管理端布局 + 一个完整的示例 CRUD（可整块删除）</li>
      </ul>
      <p class="note-tip">
        开始写业务前，建议先读 <code>docs/conventions.md</code>，
        里面写了新增接口、页面、数据表的固定步骤。
      </p>
    </section>
  </div>
</template>

<style scoped>
.hero {
  padding: 24px 0 8px;
}

.hero-title {
  margin: 0 0 10px;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.hero-sub {
  max-width: 640px;
  margin: 0 0 28px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.entry-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.entry {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.entry:hover {
  border-color: var(--app-primary);
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.1);
}

.entry-icon {
  color: var(--app-primary);
}

.entry-body {
  flex: 1;
  min-width: 0;
}

.entry-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 2px;
}

.entry-desc {
  font-size: 12px;
  color: var(--app-text-secondary);
  line-height: 1.5;
}

.entry-arrow {
  color: var(--app-text-secondary);
  opacity: 0.4;
}

.note {
  margin-top: 32px;
  padding: 20px;
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
}

.note-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}

.note-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--app-text);
}

.note-tip {
  margin: 14px 0 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.note-tip code {
  padding: 1px 5px;
  background: var(--app-bg);
  border-radius: 4px;
  font-family: Consolas, Monaco, monospace;
}

@media (max-width: 767px) {
  .hero {
    padding: 8px 0;
  }

  .hero-title {
    font-size: 24px;
  }

  .hero-sub {
    margin-bottom: 20px;
    font-size: 13px;
  }

  .entry-list {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .entry {
    padding: 14px;
  }

  .entry:hover {
    box-shadow: none;
  }

  .note {
    margin-top: 24px;
    padding: 16px;
  }
}
</style>
