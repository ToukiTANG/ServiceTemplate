<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { changeItemStatus, deleteItem, fetchItemPage } from '@/api/item'
import type { DemoItem, DemoItemQuery } from '@/types'
import { useIsMobile } from '@/utils/responsive'
import ItemFormDialog from './ItemFormDialog.vue'

/**
 * 示例列表页。
 *
 * <p>这是"新业务照着抄"的页面模板，展示了一个标准列表页该有的东西：
 * 筛选条件、表格、分页、新增/编辑弹窗、删除二次确认、状态切换。
 * 写自己的业务时，把 DemoItem 换成你的类型、把列换成你的字段即可。
 *
 * <p>手机上的处理与业务无关，属于通用做法，可以直接沿用：
 * 隐藏次要列、筛选条件竖排、分页精简。
 */

const isMobile = useIsMobile()

const loading = ref(false)
const items = ref<DemoItem[]>([])
const total = ref(0)

/**
 * 查询条件。
 *
 * 用 reactive 而不是 ref：它是个整体对象，
 * 用 ref 的话每次改字段都要 .value.xxx，读起来啰嗦。
 */
const query = reactive<DemoItemQuery>({
  pageNum: 1,
  pageSize: 10,
  name: '',
  status: undefined
})

/** 弹窗显隐 */
const dialogVisible = ref(false)

/** 正在编辑的记录，null 表示新增 */
const editing = ref<DemoItem | null>(null)

/** 拉取列表 */
async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await fetchItemPage({ ...query })
    items.value = page.list
    total.value = page.total
  } catch {
    // 错误提示由 request 拦截器统一弹出
  } finally {
    loading.value = false
  }
}

/** 搜索：回到第一页再查，否则可能停在一个不存在的页码上 */
function handleSearch(): void {
  query.pageNum = 1
  void load()
}

/** 重置条件 */
function handleReset(): void {
  query.name = ''
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
function handleEdit(row: DemoItem): void {
  editing.value = row
  dialogVisible.value = true
}

/**
 * 删除。
 *
 * 二次确认是必须的：后端是物理删除，删了就真没了。
 * 文案里带上名称，避免连续删除时看错行。
 */
async function handleDelete(row: DemoItem): Promise<void> {
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
    await deleteItem(row.id)
    ElMessage.success('删除成功')

    // 删掉当前页最后一条时，页码要往前退一页，否则会看到空列表
    if (items.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    await load()
  } catch {
    // 拦截器已提示
  }
}

/**
 * 启用 / 停用切换。
 *
 * 不在行数据上直接改 status：那样会先变界面再发请求，
 * 失败时状态就错了。这里请求成功后再刷新列表，保证界面与后端一致。
 */
async function handleToggleStatus(row: DemoItem): Promise<void> {
  const next = row.status === 1 ? 0 : 1
  try {
    await changeItemStatus(row.id, next)
    ElMessage.success(next === 1 ? '已启用' : '已停用')
    await load()
  } catch {
    // 拦截器已提示
  }
}

/** 保存成功后刷新列表 */
function handleSaved(): void {
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="item-list">
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

        <el-form-item label="状态">
          <el-select v-model="query.status" class="filter-input" placeholder="全部" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
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
        <el-button type="primary" :icon="Plus" @click="handleCreate">新增</el-button>
      </div>

      <!-- 外层允许横向滚动：窄屏下即使隐藏了次要列，仍可能放不下 -->
      <div class="table-wrap">
        <el-table v-loading="loading" :data="items" stripe style="width: 100%">
          <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />

          <el-table-column label="状态" :width="isMobile ? 70 : 90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            v-if="!isMobile"
            prop="remark"
            label="备注"
            min-width="180"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>

          <el-table-column v-if="!isMobile" prop="createTime" label="创建时间" width="170" />

          <el-table-column label="操作" :width="isMobile ? 138 : 200" fixed="right">
            <template #default="{ row }">
              <!--
                el-table 的插槽把 row 声明成 DefaultRow（索引签名类型），
                直接传给期望 DemoItem 的函数会报 TS2345。
                这里用 as 断言（纯编译期，运行时零开销）。
              -->
              <el-button link type="primary" size="small" @click="handleEdit(row as DemoItem)">
                编辑
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                @click="handleToggleStatus(row as DemoItem)"
              >
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row as DemoItem)">
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

    <ItemFormDialog v-model:visible="dialogVisible" :item="editing" @saved="handleSaved" />
  </div>
</template>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.filter-card :deep(.el-card__body) {
  padding-bottom: 2px;
}

/* 筛选输入框宽度统一由这里控制，方便小屏整体覆盖 */
.filter-input {
  width: 170px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.toolbar {
  margin-bottom: 14px;
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
    /* 竖排时标签要左对齐：默认的右对齐是给横排用的 */
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
