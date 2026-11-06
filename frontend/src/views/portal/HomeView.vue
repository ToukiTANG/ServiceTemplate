<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { fetchPortalTools } from '@/api/tool'
import type { PortalTool } from '@/types'
import { groupByCategory, isExternalLink } from '@/utils/group'

/**
 * 展示端首页：工具卡片墙。
 *
 * 数据一次性拉全量（上架工具通常几十个，没必要分页），
 * 搜索在前端做过滤，这样输入时没有任何网络延迟。
 *
 * 手机上的调整：
 * - 卡片固定单列（桌面上是 auto-fill 多列）
 * - 标题字号下调
 * - 卡片内边距和图标尺寸略缩
 * - 去掉 hover 位移（触屏没有 hover，点了会停留在一个"浮起"的怪状态）
 */

const router = useRouter()

/** 首次加载中，用来显示骨架/加载态 */
const loading = ref(true)

/** 全量工具，只加载一次 */
const tools = ref<PortalTool[]>([])

/** 搜索关键词 */
const keyword = ref('')

/**
 * 过滤 + 分组。
 *
 * 搜索匹配名称和描述，都转小写做不区分大小写的包含匹配。
 * 关键词为空时直接返回全部分组，不额外拷贝数组。
 */
const groups = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const filtered = kw
    ? tools.value.filter(
        (t) =>
          t.name.toLowerCase().includes(kw) ||
          (t.description ?? '').toLowerCase().includes(kw)
      )
    : tools.value
  return groupByCategory(filtered)
})

/** 工具总数，显示在标题旁边 */
const totalCount = computed(() => tools.value.length)

onMounted(async () => {
  try {
    tools.value = await fetchPortalTools()
  } catch {
    // 错误提示已经由 request 拦截器统一弹出，这里只需要结束加载态
  } finally {
    loading.value = false
  }
})

/**
 * 打开工具。
 *
 * 外链新开标签页（noopener 防止被打开的页面通过 window.opener 反向操作本站），
 * 站内路径交给路由处理，保持 SPA 体验。
 */
function openTool(tool: PortalTool): void {
  if (isExternalLink(tool.link)) {
    window.open(tool.link, '_blank', 'noopener,noreferrer')
  } else {
    void router.push(tool.link)
  }
}
</script>

<template>
  <div class="home">
    <section class="hero">
      <h1 class="hero-title">我的工具箱</h1>
      <p class="hero-sub">常用小工具集中在这里，点一下就能用。</p>
      <el-input
        v-model="keyword"
        class="hero-search"
        placeholder="搜索工具名称或描述"
        :prefix-icon="Search"
        clearable
      />
    </section>

    <!-- 加载中也走骨架，避免布局从空白突然跳成卡片墙 -->
    <div v-if="loading" class="tool-skeleton">
      <el-skeleton :rows="3" animated />
    </div>

    <template v-else>
      <el-empty v-if="groups.length === 0" description="没有找到匹配的工具" />

      <section v-for="group in groups" :key="group.category" class="group">
        <h2 class="group-title">
          {{ group.category }}
          <span class="group-count">{{ group.items.length }}</span>
        </h2>

        <div class="card-grid">
          <article
            v-for="tool in group.items"
            :key="tool.id"
            class="tool-card"
            role="button"
            tabindex="0"
            @click="openTool(tool)"
            @keydown.enter="openTool(tool)"
          >
            <div class="card-icon">{{ tool.icon || '🔧' }}</div>
            <div class="card-body">
              <div class="card-name">{{ tool.name }}</div>
              <div class="card-desc">{{ tool.description || '暂无描述' }}</div>
            </div>
            <div class="card-arrow">↗</div>
          </article>
        </div>
      </section>
    </template>

    <p v-if="!loading && totalCount > 0" class="total-hint">共 {{ totalCount }} 个工具</p>
  </div>
</template>

<style scoped>
.hero {
  padding: 8px 0 24px;
}

.hero-title {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 700;
}

.hero-sub {
  margin: 0 0 20px;
  color: var(--app-text-secondary);
  font-size: 14px;
}

.hero-search {
  max-width: 420px;
}

.tool-skeleton {
  padding: 40px 0;
}

.group {
  margin-bottom: 32px;
}

.group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 600;
}

.group-count {
  font-size: 12px;
  font-weight: 400;
  color: var(--app-text-secondary);
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 1px 8px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}

.tool-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}

.tool-card:hover {
  transform: translateY(-2px);
  border-color: var(--app-primary);
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.12);
}

.card-icon {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  background: var(--app-bg);
  border-radius: 10px;
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 2px;
}

.card-desc {
  font-size: 12px;
  color: var(--app-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-arrow {
  color: var(--app-text-secondary);
  opacity: 0;
  transition: opacity 0.15s;
}

.tool-card:hover .card-arrow {
  opacity: 1;
}

.total-hint {
  margin-top: 8px;
  text-align: center;
  font-size: 12px;
  color: var(--app-text-secondary);
}

/* 手机：单列卡片 + 收窄间距 + 去掉触屏上的 hover 位移 */
@media (max-width: 767px) {
  .hero {
    padding: 4px 0 18px;
  }

  .hero-title {
    font-size: 22px;
  }

  .hero-sub {
    margin-bottom: 14px;
    font-size: 13px;
  }

  .hero-search {
    max-width: 100%;
  }

  .group {
    margin-bottom: 24px;
  }

  .card-grid {
    /* 手机上不做多列：卡片里有图标 + 两行文字，两列会挤成一团 */
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .tool-card {
    padding: 12px 14px;
  }

  /* 触屏没有真正的 hover，:hover 会在点击后残留，视觉上像卡住了 */
  .tool-card:hover {
    transform: none;
    box-shadow: none;
  }

  .card-arrow {
    /* 手机上没有 hover 提示，箭头始终显示，给一点点击引导 */
    opacity: 0.5;
  }
}
</style>
