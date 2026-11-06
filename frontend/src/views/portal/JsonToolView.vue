<script setup lang="ts">
import { computed, ref } from 'vue'

/**
 * 站内工具示例：JSON 格式化。
 *
 * 这个页面的作用不只是提供一个小工具，更是演示「站内工具」的接入方式：
 * 在管理端新增工具时把 link 填成 /tools/json，它就会出现在展示页卡片墙里，
 * 点击后由前端路由跳到这里，而不是新开标签页跳出站外。
 *
 * 新增一个站内工具的标准步骤就是：在本目录建一个 View → 在 router 里注册一条子路由 →
 * 后台把 tool.link 填成对应的路径。
 */

const input = ref('')
const output = ref('')

/** 有输入内容才允许点格式化，避免空点击 */
const canFormat = computed(() => input.value.trim().length > 0)

function formatJson(): void {
  try {
    const parsed: unknown = JSON.parse(input.value)
    output.value = JSON.stringify(parsed, null, 2)
  } catch (e) {
    // 把原生的英文报错透出来，比自己编一句「格式错误」更容易定位问题
    ElMessage.error(`JSON 格式有误：${(e as Error).message}`)
  }
}

function minifyJson(): void {
  try {
    const parsed: unknown = JSON.parse(input.value)
    output.value = JSON.stringify(parsed)
  } catch (e) {
    ElMessage.error(`JSON 格式有误：${(e as Error).message}`)
  }
}

/** 清空输入和输出，回到初始状态 */
function clearAll(): void {
  input.value = ''
  output.value = ''
}

/** 把结果复制回输入框，方便接着改 */
function copyToInput(): void {
  input.value = output.value
}

async function copyOutput(): Promise<void> {
  if (!output.value) return
  try {
    await navigator.clipboard.writeText(output.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    // 非 https 环境下 clipboard API 不可用，退化成选中文本提示
    ElMessage.warning('当前环境不支持自动复制，请手动选中')
  }
}
</script>

<template>
  <div class="json-tool">
    <div class="page-head">
      <h1>JSON 格式化</h1>
      <p>粘贴 JSON，一键整理缩进或压缩。</p>
    </div>

    <div class="panes">
      <div class="pane">
        <div class="pane-head">
          <span>输入</span>
          <el-button link size="small" @click="clearAll">清空</el-button>
        </div>
        <el-input
          v-model="input"
          type="textarea"
          :rows="18"
          placeholder='例如：{"name":"hello","list":[1,2,3]}'
          spellcheck="false"
        />
      </div>

      <div class="pane-actions">
        <el-button type="primary" :disabled="!canFormat" @click="formatJson">格式化</el-button>
        <el-button :disabled="!canFormat" @click="minifyJson">压缩</el-button>
        <el-button link :disabled="!output" @click="copyToInput">结果回填</el-button>
      </div>

      <div class="pane">
        <div class="pane-head">
          <span>输出</span>
          <el-button link size="small" :disabled="!output" @click="copyOutput">复制</el-button>
        </div>
        <el-input
          v-model="output"
          type="textarea"
          :rows="18"
          readonly
          placeholder="格式化结果会显示在这里"
          spellcheck="false"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.json-tool {
  padding: 8px 0;
}

.page-head h1 {
  margin: 0 0 6px;
  font-size: 24px;
}

.page-head p {
  margin: 0 0 20px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.panes {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 16px;
  align-items: start;
}

@media (max-width: 900px) {
  .panes {
    grid-template-columns: 1fr;
  }

  .pane-actions {
    flex-direction: row;
  }
}

/* 手机：字号与内边距再收一档，三个操作按钮允许换行 */
@media (max-width: 767px) {
  .page-head h1 {
    font-size: 20px;
  }

  .pane {
    padding: 10px;
  }

  .pane-actions {
    flex-wrap: wrap;
    padding-top: 0;
    padding-bottom: 4px;
  }
}

.pane {
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  padding: 12px;
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-secondary);
}

.pane-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 24px;
}

.pane-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

:deep(.el-textarea__inner) {
  font-family: 'JetBrains Mono', Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>
