<script setup lang="ts">
import { computed, ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadFile } from 'element-plus'
import { completeUpload, initUpload, uploadChunk } from '@/api/transfer'
import type { UploadCompleteResult } from '@/types/transfer'

/**
 * 文件上传页。
 *
 * <p>流程：选文件 → init（拿 uploadId 和已传分片）→ 并发传剩余分片 → complete（拿提取码）。
 *
 * <p>几个关键设计：
 * <ul>
 *   <li><b>前端不计算真实 SHA-256</b>：500MB 文件用 Web Crypto 算哈希要把整个文件读进内存，
 *       直接 OOM。这里用「文件名 + 大小 + 修改时间」拼一个弱指纹，
 *       只用于断点续传识别，不做内容校验（内容完整性由后端合并后的 size 校验兜底）。</li>
 *   <li><b>并发 3 个分片</b>：串行太慢（100 片要等很久），并发太多会把服务器和浏览器都拖住。</li>
 *   <li><b>进度按"已完成分片数 / 总分片数"算</b>，不按字节，避免每片回调频率太高导致界面卡顿。</li>
 * </ul>
 */

/** 与后端 app.transfer.max-file-size 保持一致 */
const MAX_SIZE = 500 * 1024 * 1024

/** 同时上传的分片数 */
const CONCURRENCY = 3

const file = ref<File | null>(null)
const expireHours = ref(24)
const uploading = ref(false)
const progress = ref(0)
const result = ref<UploadCompleteResult | null>(null)

/** 当前阶段的提示文案 */
const stageText = computed(() => {
  if (!uploading.value) return ''
  if (progress.value >= 100) return '正在合并分片并校验…'
  return `正在上传 ${progress.value}%`
})

/** 把字节格式化成人类可读的大小 */
function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${bytes} B`
}

/**
 * 生成弱指纹，用于让后端识别"这是同一个文件，接着上次传"。
 *
 * 用 djb2 把长字符串压成一个短哈希，再用文件大小做后缀，
 * 保证长度稳定在 20 字符以内（后端字段上限 64），且不同文件碰撞概率极低。
 */
function weakKey(f: File): string {
  const raw = `${f.name}|${f.size}|${f.lastModified}`
  let hash = 5381
  for (let i = 0; i < raw.length; i++) {
    hash = ((hash << 5) + hash + raw.charCodeAt(i)) >>> 0
  }
  return `k${hash.toString(16)}s${f.size.toString(16)}`
}

/**
 * 选择文件回调。
 *
 * el-upload 设了 auto-upload=false，所以这里只是把文件接过来由我们自己控制上传。
 */
function handleFileChange(uploadFile: UploadFile): void {
  const raw = uploadFile.raw
  if (!raw) return

  if (raw.size === 0) {
    ElMessage.error('不能上传空文件')
    return
  }
  if (raw.size > MAX_SIZE) {
    ElMessage.error(`文件超过 ${formatSize(MAX_SIZE)} 上限`)
    return
  }

  file.value = raw
  result.value = null
  progress.value = 0
}

/**
 * 开始上传。
 *
 * 分片并发用一个共享队列 + 若干 worker 实现：
 * 每个 worker 不断从队列取下一个分片，直到队列空。
 * 这比"每 3 个一组 await"要好——分组的方式会被最慢的那个分片拖住整组。
 */
async function startUpload(): Promise<void> {
  const f = file.value
  if (!f || uploading.value) return

  uploading.value = true
  progress.value = 0

  try {
    const init = await initUpload({
      fileName: f.name,
      fileSize: f.size,
      fileHash: weakKey(f),
      contentType: f.type || 'application/octet-stream',
      expireHours: expireHours.value
    })

    const { uploadId, uploadedChunks, chunkSize, totalChunks } = init
    const finished = new Set(uploadedChunks)
    let completed = finished.size

    if (finished.size > 0) {
      ElMessage.info(`检测到已上传 ${finished.size}/${totalChunks} 个分片，继续续传`)
    }
    progress.value = Math.round((completed / totalChunks) * 100)

    // 待传队列
    const queue: number[] = []
    for (let i = 0; i < totalChunks; i++) {
      if (!finished.has(i)) queue.push(i)
    }

    const workerCount = Math.min(CONCURRENCY, Math.max(queue.length, 1))
    const workers = Array.from({ length: workerCount }, async () => {
      while (queue.length > 0) {
        const index = queue.shift()
        if (index === undefined) break

        const start = index * chunkSize
        const end = Math.min(start + chunkSize, f.size)
        const blob = f.slice(start, end)

        await uploadChunk(uploadId, index, blob)
        completed++
        progress.value = Math.round((completed / totalChunks) * 100)
      }
    })
    await Promise.all(workers)

    result.value = await completeUpload(uploadId)
    ElMessage.success('上传完成')
  } catch {
    // 具体错误已由 request 拦截器统一提示，这里只需要恢复按钮状态
  } finally {
    uploading.value = false
  }
}

/** 重新选一个文件 */
function reset(): void {
  file.value = null
  result.value = null
  progress.value = 0
}

/** 提取码复制到剪贴板 */
async function copyCode(): Promise<void> {
  if (!result.value) return
  try {
    await navigator.clipboard.writeText(result.value.code)
    ElMessage.success('提取码已复制')
  } catch {
    // 非 https 环境下 clipboard 不可用，退化成提示用户手动选中
    ElMessage.warning('当前环境不支持自动复制，请手动选中')
  }
}

/** 完整分享链接：用当前域名拼，不做后端配置，换域名不用改代码 */
const shareUrl = computed(() => {
  if (!result.value) return ''
  return `${window.location.origin}/transfer/${result.value.code}`
})

async function copyLink(): Promise<void> {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('当前环境不支持自动复制，请手动选中')
  }
}
</script>

<template>
  <div class="transfer-page">
    <div class="page-head">
      <h1>文件传输</h1>
      <p>上传文件生成提取码，对方打开本站输入提取码即可下载。单文件最大 500MB，到期自动删除。</p>
    </div>

    <!-- 上传成功：只显示提取码卡片，把选文件区域收起来，避免误操作 -->
    <el-card v-if="result" shadow="never" class="result-card">
      <div class="result-title">上传成功</div>

      <div class="file-line">
        <span class="file-name">{{ result.fileName }}</span>
        <span class="file-meta">{{ formatSize(result.fileSize) }}</span>
      </div>

      <div class="code-box">
        <div class="code-label">提取码</div>
        <div class="code-value">{{ result.code }}</div>
        <el-button type="primary" plain @click="copyCode">复制提取码</el-button>
      </div>

      <div class="share-line">
        <el-input :model-value="shareUrl" readonly class="share-input" />
        <el-button @click="copyLink">复制链接</el-button>
      </div>

      <div class="expire-line">有效期至 {{ result.expireTime }}</div>

      <el-button class="again-btn" @click="reset">继续上传其他文件</el-button>
    </el-card>

    <!-- 上传表单 -->
    <template v-else>
      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="false"
        :disabled="uploading"
        class="upload-box"
        @change="handleFileChange"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">把文件拖到这里，或<em>点击选择</em></div>
        <div class="upload-hint">单文件最大 500MB，支持断点续传</div>
      </el-upload>

      <div v-if="file" class="file-info">
        <div class="file-line">
          <span class="file-name">{{ file.name }}</span>
          <span class="file-meta">{{ formatSize(file.size) }}</span>
        </div>
        <el-button link type="danger" :disabled="uploading" @click="reset">移除</el-button>
      </div>

      <div class="form-line">
        <span class="form-label">有效期</span>
        <el-radio-group v-model="expireHours" :disabled="uploading">
          <el-radio-button :value="24">24 小时</el-radio-button>
          <el-radio-button :value="168">7 天</el-radio-button>
        </el-radio-group>
      </div>

      <div v-if="uploading" class="progress-line">
        <el-progress :percentage="progress" :stroke-width="8" />
        <div class="stage-text">{{ stageText }}</div>
      </div>

      <el-button
        type="primary"
        size="large"
        class="submit-btn"
        :disabled="!file || uploading"
        :loading="uploading"
        @click="startUpload"
      >
        {{ uploading ? '上传中…' : '开始上传' }}
      </el-button>
    </template>
  </div>
</template>

<style scoped>
.transfer-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 8px 0;
}

.page-head h1 {
  margin: 0 0 6px;
  font-size: 24px;
}

.page-head p {
  margin: 0 0 24px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-text-secondary);
}

.upload-box :deep(.el-upload-dragger) {
  padding: 40px 20px;
  border-radius: var(--app-radius);
}

.upload-icon {
  font-size: 44px;
  color: var(--app-text-secondary);
}

.upload-text {
  margin-top: 8px;
  font-size: 14px;
  color: var(--app-text);
}

.upload-text em {
  color: var(--app-primary);
  font-style: normal;
}

.upload-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.file-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
}

.file-line {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.form-line {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 18px 0;
}

.form-label {
  font-size: 14px;
  color: var(--app-text-secondary);
}

.progress-line {
  margin: 18px 0;
}

.stage-text {
  margin-top: 6px;
  font-size: 12px;
  color: var(--app-text-secondary);
  text-align: center;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
}

/* ---------- 结果卡片 ---------- */
.result-card {
  text-align: center;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: #67c23a;
  margin-bottom: 12px;
}

.code-box {
  margin: 20px 0;
  padding: 20px;
  background: var(--app-bg);
  border-radius: var(--app-radius);
}

.code-label {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.code-value {
  margin: 8px 0 14px;
  font-size: 36px;
  font-weight: 700;
  letter-spacing: 6px;
  font-family: Consolas, Monaco, monospace;
  color: var(--app-primary);
}

.share-line {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.share-input :deep(.el-input__inner) {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
}

.expire-line {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.again-btn {
  margin-top: 20px;
}

@media (max-width: 767px) {
  .page-head h1 {
    font-size: 20px;
  }

  .code-value {
    font-size: 28px;
    letter-spacing: 4px;
  }

  .share-line {
    flex-direction: column;
  }
}
</style>
