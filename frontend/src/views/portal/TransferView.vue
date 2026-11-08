<script setup lang="ts">
import { computed, ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadFile } from 'element-plus'
import { uploadAndShare } from '@/api/transfer'
import type { TransferInfo } from '@/types/transfer'

/**
 * 文件上传页。
 *
 * <p>改造后文件**不再经过服务器**：浏览器直接 PUT 到又拍云，服务器只发签名。
 * 页面逻辑因此变得简单——没有本地分片落盘、没有断点续传的状态机，
 * 整个上传收敛成 {@link uploadAndShare} 一次调用。
 *
 * <p>又拍云协议的两个约束在页面里是"看不见"的，但要知道它们存在：
 * 分片固定 1MB、只支持顺序上传。所以进度是"已完成片数 / 总片数"，
 * 而不是按字节——后者需要额外的流式统计，收益不大。
 */

/** 与后端 app.storage.upyun.max-file-size 保持一致，前端提前拦掉超大文件 */
const MAX_SIZE = 500 * 1024 * 1024

const file = ref<File | null>(null)
const expireHours = ref(24)
const uploading = ref(false)
const progress = ref(0)
const result = ref<TransferInfo | null>(null)

/** 把字节格式化成人类可读的大小 */
function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${bytes} B`
}

/** 选择文件（el-upload 设了 auto-upload=false，这里只是接过来） */
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
 * <p>失败时不需要清理服务端状态：上传记录停在「上传中」，
 * 由脚手架的孤儿清理任务在 24 小时后回收。
 */
async function startUpload(): Promise<void> {
  const f = file.value
  if (!f || uploading.value) return

  uploading.value = true
  progress.value = 0

  try {
    result.value = await uploadAndShare(f, expireHours.value, (percent) => {
      progress.value = percent
    })
    ElMessage.success('上传完成')
  } catch (e) {
    // request 拦截器已经弹过网络/业务错误；这里只处理直传又拍云失败的情况
    const msg = e instanceof Error ? e.message : ''
    if (msg) {
      ElMessage.error(msg)
    }
  } finally {
    uploading.value = false
  }
}

/** 重新选文件 */
function reset(): void {
  file.value = null
  result.value = null
  progress.value = 0
}

/** 复制提取码 */
async function copyCode(): Promise<void> {
  if (!result.value) return
  try {
    await navigator.clipboard.writeText(result.value.code)
    ElMessage.success('提取码已复制')
  } catch {
    ElMessage.warning('当前环境不支持自动复制，请手动选中')
  }
}

/** 完整分享链接：用当前域名拼 */
const shareUrl = computed(() => {
  if (!result.value) return ''
  return `${window.location.origin}/transfer/${result.value.code}`
})

/** 复制分享链接 */
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

    <!-- 上传成功 -->
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
        <div class="upload-hint">单文件最大 500MB，直传对象存储不占用服务器带宽</div>
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
        <div class="stage-text">
          {{ progress >= 100 ? '正在生成提取码…' : `正在上传 ${progress}%` }}
        </div>
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
