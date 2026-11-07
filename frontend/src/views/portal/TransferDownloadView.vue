<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Download } from '@element-plus/icons-vue'
import { buildDownloadUrl, fetchTransferInfo } from '@/api/transfer'
import type { TransferInfo } from '@/types/transfer'

/**
 * 文件提取页，路径 {@code /transfer/:code}。
 *
 * <p>给收文件的人用：从链接进来就直接查到文件，点一下下载。
 * 这个页面是公开的（后端 {@code /api/transfer/info/**} 在白名单里），
 * 对方不需要有账号。
 */

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const info = ref<TransferInfo | null>(null)
const errorText = ref('')

/** 提取码来自路径参数，可能是数组（理论上不会），统一转成字符串 */
const code = Array.isArray(route.params.code) ? route.params.code[0] : String(route.params.code ?? '')

onMounted(async () => {
  if (!code) {
    errorText.value = '提取码为空'
    loading.value = false
    return
  }

  try {
    info.value = await fetchTransferInfo(code)
  } catch (e) {
    // 后端对"不存在 / 已过期 / 未完成"返回同一个错误码，这里直接用它的 message
    errorText.value = (e as Error).message || '文件不存在或已过期'
  } finally {
    loading.value = false
  }
})

/**
 * 触发下载。
 *
 * 用 location 跳转而不是 axios 拉 blob：
 * 浏览器会自己处理保存对话框和下载管理器，也不会把几百 MB 的文件放进页面内存。
 * 因为响应头带 Content-Disposition: attachment，页面本身不会跳走。
 */
function handleDownload(): void {
  window.location.href = buildDownloadUrl(code)
}

/** 下载失败/过期时回到上传页 */
function goHome(): void {
  void router.push('/transfer')
}

/** 把字节格式化成易读大小 */
function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${bytes} B`
}
</script>

<template>
  <div class="download-page">
    <el-card v-loading="loading" shadow="never" class="download-card">
      <template v-if="info">
        <div class="file-icon">📦</div>
        <h2 class="file-name">{{ info.fileName }}</h2>
        <div class="file-meta">
          {{ formatSize(info.fileSize) }} · 已下载 {{ info.downloadCount }} 次
        </div>

        <el-button type="primary" size="large" :icon="Download" class="download-btn" @click="handleDownload">
          下载文件
        </el-button>

        <div class="expire-line">有效期至 {{ info.expireTime }}</div>
      </template>

      <template v-else-if="!loading">
        <div class="empty-icon">🕳️</div>
        <h2 class="empty-title">文件不存在或已过期</h2>
        <p class="empty-desc">{{ errorText || '提取码可能输错了，或者文件已被清理' }}</p>
        <el-button @click="goHome">去上传文件</el-button>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.download-page {
  max-width: 520px;
  margin: 40px auto;
  padding: 0 16px;
}

.download-card {
  text-align: center;
  padding: 12px 0 8px;
}

.file-icon,
.empty-icon {
  font-size: 48px;
  line-height: 1;
  margin-bottom: 16px;
}

.file-name {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  word-break: break-all;
}

.file-meta {
  font-size: 13px;
  color: var(--app-text-secondary);
  margin-bottom: 24px;
}

.download-btn {
  width: 100%;
  max-width: 280px;
}

.expire-line {
  margin-top: 16px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.empty-title {
  margin: 0 0 8px;
  font-size: 17px;
  font-weight: 600;
}

.empty-desc {
  margin: 0 0 20px;
  font-size: 13px;
  color: var(--app-text-secondary);
}
</style>
