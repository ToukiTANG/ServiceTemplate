<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { changeToolStatus, deleteTool, fetchToolPage } from '@/api/tool'
import type { Tool, ToolQuery } from '@/types'
import { useIsMobile } from '@/utils/responsive'
import ToolFormDialog from './ToolFormDialog.vue'

/**
 * 工具列表页。
 *
 * 页面职责只有三件事：查、改、删。
 * 表单的字段校验、提交逻辑全部交给 ToolFormDialog，
 * 这里只负责打开弹窗和接收「保存成功」的通知。
 *
 * 手机上做了三处收缩（否则表格会横着溢出屏幕）：
 * 1. 隐藏「描述 / 链接 / 创建时间」三列，只留图标、名称、分类、排序、状态、操作
 * 2. 筛选条件的输入框改为整行宽，不再并排
 * 3. 分页只保留上一页/页码/下一页，去掉总数与每页条数选择
 * 表格本身仍可横向滚动，作为极端窄屏下的兜底
 */

const isMobile = useIsMobile()

const loading = ref(false)
const tools = ref<Tool[]>([])
const total = ref(0)

/**
 * 查询条件。
 *
 * 用 reactive 而不是 ref，是因为它是个整体对象，
 * 用 ref 的话每次改字段都要 .value.xxx，读起来啰嗦。
 * 注意与分页相关的 pageNum 也要放在这里，翻页时直接改。
 */
const query = reactive<ToolQuery>({
  pageNum: 1,
  pageSize: 10,
  name: '',
  category: '',
  status: undefined
})

/** 弹窗显隐 */
const dialogVisible = ref(false)

/** 正在编辑的工具，null 表示新增 */
const editing = ref<Tool | null>(null)

/**
 * 拉取列表。
 *
 * 查询条件里有空字符串，直接传给后端没问题（后端做了空串转 null 的处理），
 * 前端这边不做额外清洗，保持逻辑简单。
 */
async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await fetchToolPage({ ...query })
    tools.value = page.list
    total.value = page.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/** 搜索：回到第一页再查，否则可能停在一个不存在的页码上 */
function handleSearch(): void {
  query.pageNum = 1
  void load()
}

/** 重置：清空条件并重新查询 */
function handleReset(): void {
  query.name = ''
  query.category = ''
  query.status = undefined
  query.pageNum = 1
  void load()
}

/** 打开新增弹窗 */
function handleCreate(): void {
  editing.value = null
  dialogVisible.value = true
}

/** 打开编辑弹窗 */
function handleEdit(row: Tool): void {
  editing.value = row
  dialogVisible.value = true
}

/**
 * 删除。
 *
 * 二次确认是必须的：后端是物理删除，删了就真没了，没有回收站。
 * 确认文案里带上工具名，避免连续删除时看错行。
 */
async function handleDelete(row: Tool): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return
  }

  try {
    await deleteTool(row.id)
    ElMessage.success('删除成功')

    // 删掉当前页最后一条时，页码要往前退一页，否则会看到空列表
    if (tools.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    await load()
  } catch {
    // 拦截器已提示
  }
}

/**
 * 上架 / 下架切换。
 *
 * 直接在行数据上改 status 会让表格先变再请求，失败时状态就错了。
 * 所以这里请求成功后再刷列表，保证界面与后端一致。
 */
async function handleToggleStatus(row: Tool): Promise<void> {
  const next = row.status === 1 ? 0 : 1
  try {
    await changeToolStatus(row.id, next)
    ElMessage.success(next === 1 ? '已上架' : '已下架')
    await load()
  } catch {
    // 拦截器已提示
  }
}

/** 保存成功后刷新列表（新增时留在当前页，编辑时刷新当前页） */
function handleSaved(): void {
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="tool-list">
    <el-card shadow="never" class="filter-card">
      <el-form :inline="!isMobile" class="filter-form" @submit.prevent="handleSearch">
        <el-form-item label="名称">
          <el-input
            v-model="query.name"
            class="filter-input"
            placeholder="按名称搜索"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="分类">
          <el-input
            v-model="query.category"
            class="filter-input"
            placeholder="按分类搜索"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="query.status" class="filter-input" placeholder="全部" clearable>
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>

        <el-form-item class="filter-actions">
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="handleCreate">新增工具</el-button>
      </div>

      <!-- 外层容器允许横向滚动：窄屏下即使隐藏了次要列，仍可能放不下 -->
      <div class="table-wrap">
        <el-table v-loading="loading" :data="tools" stripe style="width: 100%">
          <el-table-column v-if="!isMobile" label="图标" width="64" align="center">
            <template #default="{ row }">
              <span class="cell-icon">{{ row.icon || '🔧' }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="name" label="名称" min-width="90" show-overflow-tooltip />

          <el-table-column
            v-if="!isMobile"
            prop="description"
            label="描述"
            min-width="180"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ row.description || '—' }}</template>
          </el-table-column>

          <el-table-column v-if="!isMobile" prop="category" label="分类" width="100" />

          <el-table-column
            v-if="!isMobile"
            prop="link"
            label="链接"
            min-width="180"
            show-overflow-tooltip
          />

          <el-table-column v-if="!isMobile" prop="sort" label="排序" width="72" align="center" />

          <el-table-column label="状态" :width="isMobile ? 64 : 86" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '上架' : '下架' }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            v-if="!isMobile"
            prop="createTime"
            label="创建时间"
            width="170"
          />

          <el-table-column label="操作" :width="isMobile ? 138 : 200" fixed="right">
            <template #default="{ row }">
              <!--
                el-table 的插槽把 row 声明成 DefaultRow（索引签名类型），
                直接传给期望 Tool 的函数会报 TS2345。
                这里用 as 断言（纯编译期，运行时零开销）。
              -->
              <el-button link type="primary" size="small" @click="handleEdit(row as Tool)">编辑</el-button>
              <el-button link type="primary" size="small" @click="handleToggleStatus(row as Tool)">
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row as Tool)">删除</el-button>
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

    <ToolFormDialog v-model:visible="dialogVisible" :tool="editing" @saved="handleSaved" />
  </div>
</template>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.filter-card :deep(.el-card__body) {
  padding-bottom: 2px;
}

/* 筛选输入框宽度统一由这里控制，不再写内联 style，方便小屏整体覆盖 */
.filter-input {
  width: 170px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.toolbar {
  margin-bottom: 14px;
}

.cell-icon {
  font-size: 18px;
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

  /* 手机上 label 和输入框竖排，避免 label 挤掉输入框宽度 */
  .filter-form :deep(.el-form-item) {
    display: block;
    margin-right: 0;
  }

  .filter-form :deep(.el-form-item__label) {
    display: block;
    width: auto;
    padding: 0 0 4px;
    line-height: 1.5;
    /* 竖排时标签要左对齐：默认的右对齐是给横排用的，竖排时看起来像浮在框外 */
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
