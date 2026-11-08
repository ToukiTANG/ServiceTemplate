<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import { deleteTransfer, fetchTransferPage } from '@/api/transfer'
import type { TransferAdminItem, TransferQuery } from '@/types/transfer'
import { useIsMobile } from '@/utils/responsive'

/**
 * 管理端：文件传输记录。
 *
 * <p>能看到所有传输记录，可以按状态筛选，可以手动删除。
 * 文件名来自关联的上传记录——本表只存业务属性（提取码、有效期、下载次数）。
 * 删除会同时删磁盘文件——这个操作不可恢复，所以做了二次确认。
 */

const isMobile = useIsMobile()

const loading = ref(false)
const list = ref<TransferAdminItem[]>([])
const total = ref(0)

const query = reactive<TransferQuery>({
  pageNum: 1,
  pageSize: 10,
  status: undefined
})

/** 拉取列表 */
async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await fetchTransferPage({ ...query })
    list.value = page.list
    total.value = page.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  query.pageNum = 1
  void load()
}

function handleReset(): void {
  query.status = undefined
  query.pageNum = 1
  void load()
}

/**
 * 删除记录。
 *
 * 文案里同时给出文件名和提取码：管理端列表里同一天可能有多个同名文件，
 * 只显示文件名容易删错。
 */
async function handleDelete(row: TransferAdminItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.fileName}」（提取码 ${row.code}）吗？对象存储上的文件会一起删除，不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }

  try {
    await deleteTransfer(row.id)
    ElMessage.success('已删除')
    if (list.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    await load()
  } catch {
    // 拦截器已提示
  }
}

/** 状态映射成标签文案与颜色。改造后只有两种：可用 / 已失效 */
function statusMeta(status: number): { text: string; type: 'success' | 'info' } {
  if (status === 1) return { text: '可用', type: 'success' }
  return { text: '已失效', type: 'info' }
}

/** 字节转易读大小 */
function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${bytes} B`
}

/**
 * 拼完整的下载链接。
 *
 * 用 {@code window.location.origin} 而不是配置里的域名：
 * 管理端从哪个域名打开，生成的就是那个域名，本地调试和线上部署都不用改配置。
 */
function buildLink(code: string): string {
  return `${window.location.origin}/transfer/${code}`
}

/**
 * 复制分享链接到剪贴板。
 *
 * 复制的是「本站提取页」的链接（/transfer/{code}），不是对象存储直链——
 * 发给别人的应该是干净的短链，而不是带随机路径的 CDN 地址。
 */
async function copyLink(row: TransferAdminItem): Promise<void> {
  if (!row.code) {
    ElMessage.warning('该记录还没有提取码')
    return
  }

  try {
    await navigator.clipboard.writeText(buildLink(row.code))
    ElMessage.success('链接已复制')
  } catch {
    // 非 https 环境（且非 localhost）下 clipboard API 不可用
    ElMessage.warning('当前环境不支持自动复制，请手动选中链接')
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="transfer-list">
    <el-card shadow="never" class="filter-card">
      <el-form :inline="!isMobile" class="filter-form" @submit.prevent="handleSearch">
        <el-form-item label="状态">
          <el-select v-model="query.status" class="filter-input" placeholder="全部" clearable>
            <el-option label="可用" :value="1" />
            <el-option label="已失效" :value="2" />
          </el-select>
        </el-form-item>

        <el-form-item class="filter-actions">
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="table-wrap">
        <el-table v-loading="loading" :data="list" stripe style="width: 100%">
          <el-table-column prop="fileName" label="文件名" :min-width="isMobile ? 90 : 160" show-overflow-tooltip />

          <el-table-column v-if="!isMobile" label="大小" width="100">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>

          <el-table-column prop="code" label="提取码" :width="isMobile ? 96 : 110">
            <template #default="{ row }">
              <span class="code-cell">{{ row.code }}</span>
            </template>
          </el-table-column>

          <!--
            下载链接：这是发给别人的东西，比提取码更常用，所以单独一列。
            桌面上显示省略了域名的短路径，手机整列隐藏（长度会挤没别的列）。
          -->
          <el-table-column v-if="!isMobile" label="下载链接" width="200">
            <template #default="{ row }">
              <div class="link-cell">
                <span class="link-text" :title="buildLink(row.code)">
                  {{ row.code ? '/transfer/' + row.code : '—' }}
                </span>
                <el-button link type="primary" size="small" @click="copyLink(row as TransferAdminItem)">
                  复制
                </el-button>
              </div>
            </template>
          </el-table-column>

          <el-table-column v-if="!isMobile" prop="downloadCount" label="下载" width="80" align="center" />

          <el-table-column v-if="!isMobile" label="状态" width="96" align="center">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.status).type" size="small">
                {{ statusMeta(row.status).text }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column v-if="!isMobile" prop="expireTime" label="过期时间" width="160" show-overflow-tooltip />
          <el-table-column v-if="!isMobile" prop="createTime" label="上传时间" width="160" show-overflow-tooltip />

          <el-table-column label="操作" :width="isMobile ? 128 : 100" fixed="right">
            <template #default="{ row }">
              <!--
                手机上"下载链接"整列被隐藏了，把复制按钮收到这里。
                fixed="right" 保证它永远贴着右侧可见，不会被横向滚动挤走。
              -->
              <el-button
                v-if="isMobile"
                link
                type="primary"
                size="small"
                @click="copyLink(row as TransferAdminItem)"
              >
                复制
              </el-button>
              <el-button link type="danger" size="small" :icon="Delete" @click="handleDelete(row as TransferAdminItem)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
          :pager-count="isMobile ? 5 : 7"
          :small="isMobile"
          background
          @current-change="load"
          @size-change="handleSearch"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.filter-card :deep(.el-card__body) {
  padding-bottom: 2px;
}

.filter-input {
  width: 180px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.code-cell {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  letter-spacing: 1px;
}

.link-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.link-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.table-wrap {
  width: 100%;
  overflow-x: auto;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 767px) {
  .filter-input {
    width: 100%;
  }

  .filter-form :deep(.el-form-item) {
    display: block;
    margin-right: 0;
  }

  .filter-form :deep(.el-form-item__label) {
    display: block;
    width: auto;
    padding: 0 0 4px;
    line-height: 1.5;
    text-align: left;
    justify-content: flex-start;
  }

  .filter-form :deep(.el-form-item__content) {
    display: block;
  }

  .filter-actions {
    margin-bottom: 12px;
  }

  .filter-actions :deep(.el-form-item__content) {
    display: flex;
    gap: 8px;
  }

  .pager {
    justify-content: center;
  }
}
</style>
