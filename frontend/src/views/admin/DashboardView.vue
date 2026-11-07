<script setup lang="ts">
import { computed } from 'vue'
import { Document, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

/**
 * 管理端工作台（首页）。
 *
 * <p>脚手架本身没有任何业务，所以这里就一个说明页：告诉使用者接下来该往哪儿写代码。
 * 有了第一个业务页面后，把这个文件换成真正的 Dashboard（统计卡片、待办、快捷入口）。
 *
 * <p>管理端菜单不是写死在这里的，而是在 {@code router/index.ts} 里给路由加
 * {@code meta: { menu: true, title, icon, order }}，菜单会自动出现。
 */

const userStore = useUserStore()

/** 当前登录用户的显示名 */
const displayName = computed(() => userStore.displayName)

/** 接口文档地址。生产环境关掉 Swagger 后这个按钮就没意义了，可以一并删掉 */
function openDocs(): void {
  window.open('/swagger-ui.html', '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <div class="dashboard">
    <el-card shadow="never" class="welcome-card">
      <h2 class="welcome-title">你好，{{ displayName }}</h2>
      <p class="welcome-sub">
        这是脚手架自带的管理端骨架。目前还没有任何业务页面，左侧菜单里只有这个工作台。
      </p>

      <div class="steps">
        <div class="step">
          <span class="step-no">1</span>
          <div class="step-body">
            <div class="step-title">建页面</div>
            <div class="step-desc">在 <code>src/views/admin/</code> 下新建 <code>.vue</code> 文件</div>
          </div>
        </div>
        <div class="step">
          <span class="step-no">2</span>
          <div class="step-body">
            <div class="step-title">注册路由</div>
            <div class="step-desc">
              在 <code>src/router/index.ts</code> 的 <code>/admin</code> 子路由里加一条
            </div>
          </div>
        </div>
        <div class="step">
          <span class="step-no">3</span>
          <div class="step-body">
            <div class="step-title">加菜单</div>
            <div class="step-desc">
              给路由加上 <code>meta: { menu: true, title: 'xxx', icon: 'House' }</code>，
              菜单会自动出现
            </div>
          </div>
        </div>
      </div>

      <div class="actions">
        <el-button type="primary" :icon="Document" @click="openDocs">打开接口文档</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="tip-card">
      <div class="tip-title">
        <el-icon><Plus /></el-icon>
        后端从哪开始
      </div>
      <p class="tip-desc">
        新建一个业务包（比如 <code>com.touki.order</code>），按
        <code>Controller → Service → Mapper</code> 三层写；
        错误码放在自己的枚举里（实现 <code>ErrorItem</code>），不要往 <code>common.ErrorCode</code> 里塞业务码。
        具体步骤见 <code>docs/conventions.md</code>。
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 860px;
}

.welcome-card {
  margin-bottom: 16px;
}

.welcome-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
}

.welcome-sub {
  margin: 0 0 24px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.steps {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.step {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.step-no {
  flex: 0 0 22px;
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: var(--app-primary);
  border-radius: 50%;
}

.step-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 2px;
}

.step-desc {
  font-size: 12px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.step-desc code,
.tip-desc code {
  padding: 1px 5px;
  background: var(--app-bg);
  border-radius: 4px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.actions {
  margin-top: 24px;
}

.tip-card {
  background: #fafcff;
}

.tip-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.tip-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--app-text-secondary);
}

@media (max-width: 767px) {
  .welcome-title {
    font-size: 18px;
  }

  .welcome-sub {
    margin-bottom: 18px;
  }
}
</style>
