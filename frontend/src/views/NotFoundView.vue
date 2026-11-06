<script setup lang="ts">
import { useRouter } from 'vue-router'

/**
 * 404 页面。
 *
 * 用户手滑改错地址、或者工具被删后从旧链接进来，都会落到这里。
 * 给一个明确的返回入口，避免停在死路上。
 */
const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="code">404</div>
    <p class="tip">页面不存在，可能已经被删掉了</p>
    <el-button type="primary" @click="router.replace('/')">回到首页</el-button>
  </div>
</template>

<style scoped>
.not-found {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.code {
  font-size: 72px;
  font-weight: 700;
  color: var(--app-border);
  line-height: 1;
}

.tip {
  margin: 0 0 8px;
  color: var(--app-text-secondary);
}
</style>
