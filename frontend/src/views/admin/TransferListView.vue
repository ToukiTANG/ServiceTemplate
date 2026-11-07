<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import { deleteTransfer, fetchTransferPage } from '@/api/transfer'
import type { TransferAdminItem, TransferQuery } from '@/types/transfer'
import { useIsMobile } from '@/utils/responsive'

/**
 * 管理端：文件传输记录。
 *
 * <p>能看到所有记录（包括还在上传中的），可以按文件名和状态筛选，可以手动删除。
 * 删除会同时删磁盘文件——这个操作不可恢复，所以做了二次确认。
 */

const isMobile = useIsMobile()

const loading = ref(false)
const list = ref<TransferAdminItem[]>([])
const total = ref(0)

const query = reactive<TransferQuery>({
  pageNum: 1,
  pageSize: 10,
  fileName: '',
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
  query.fileName = ''
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
      `确定删除「${row.fileName}」（提取码 ${row.code}）吗？磁盘文件会一起删除，不可恢复。`,
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

/** 状态映射成标签文案与颜色 */
function statusMeta(status: number): { text: string; type: 'success' | 'info' | 'warning' } {
  if (status === 1) return { text: '可下载', type: 'success' }
  if (status === 0) return { text: '上传中', type: 'warning' }
  return { text: '已删除', type: 'info' }
}

/** 字节转易读大小 */
function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${bytes} B`
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="transfer-list">
    <el-card shadow="never" class="filter-card">
      <el-form :inline="!isMobile" class="filter-form" @submit.prevent="handleSearch">
        <el-form-item label="文件名">
          <el-input
            v-model="query.fileName"
            class="filter-input"
            placeholder="按文件名搜索"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="query.status" class="filter-input" placeholder="全部" clearable>
            <el-option label="上传中" :value="0" />
            <el-option label="可下载" :value="1" />
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
          <el-table-column prop="fileName" label="文件名" min-width="160" show-overflow-tooltip />

          <el-table-column label="大小" width="100">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>

          <el-table-column prop="code" label="提取码" width="110">
            <template #default="{ row }">
              <span class="code-cell">{{ row.code }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="downloadCount" label="下载" width="80" align="center" />

          <el-table-column label="状态" width="96" align="center">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.status).type" size="small">
                {{ statusMeta(row.status).text }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column v-if="!isMobile" prop="expireTime" label="过期时间" width="170" />
          <el-table-column v-if="!isMobile" prop="createTime" label="上传时间" width="170" />

          <el-table-column label="操作" :width="isMobile ? 90 : 100" fixed="right">
            <template #default="{ row }">
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
