<script setup lang="ts">
import { ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { createTool, updateTool } from '@/api/tool'
import type { Tool, ToolPayload } from '@/types'
import { useIsMobile } from '@/utils/responsive'

/**
 * 工具新增/编辑弹窗。
 *
 * 新增和编辑共用一个组件：靠 props.tool 是否有 id 来区分模式。
 * 这样表单字段、校验规则只需要维护一份，不会出现「新增能填的字段编辑时漏了」这种问题。
 */

const props = defineProps<{
  /** 弹窗是否可见，配合 v-model:visible 使用 */
  visible: boolean
  /** 编辑时传入的工具；新增时为 null */
  tool: Tool | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  /** 保存成功后通知父组件刷新列表 */
  (e: 'saved'): void
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)

/**
 * 是否手机尺寸。
 *
 * 弹窗在手机上不能用固定 560px：375px 宽的屏幕会直接溢出，
 * Element Plus 的 dialog 不会自动缩，必须显式给百分比宽度。
 * 同时 label 从左侧改到顶部，否则「名称 / 分类 / 链接」这类两字标签
 * 会占掉一半可用宽度，输入框只剩一百多像素。
 */
const isMobile = useIsMobile()

/**
 * 表单模型。
 *
 * 与 ToolPayload 的区别是：这里所有字段都是必填的。
 * 原因是表单控件对类型很敏感——el-input 的 v-model 要 string，
 * el-input-number 要 number，如果字段声明成可选（string | undefined），
 * 模板里每个 v-model 都会报类型错误。
 * 提交时再转成 ToolPayload，把空字符串统一转成 undefined。
 */
interface ToolFormModel {
  name: string
  description: string
  icon: string
  link: string
  category: string
  sort: number
  status: number
}

/** 表单模型，初始值为一套空数据 */
function emptyForm(): ToolFormModel {
  return {
    name: '',
    description: '',
    icon: '',
    link: '',
    category: '',
    sort: 0,
    status: 1
  }
}

/**
 * 用 ref 承载整个表单对象，而不是 reactive。
 *
 * 两者在 script 里差别不大（ref 多一层 .value），但在模板的类型推断上差别明显：
 * vue-tsc 处理 reactive 对象的 v-model 绑定时，会给部分属性报
 * "Property 'value' does not exist" 这种找不到根因的错误；
 * 换成 ref 后类型链路是直的，模板里 form.name 依然自动解包，不用写 .value。
 */
const form = ref<ToolFormModel>(emptyForm())

/** 当前是否为编辑模式 */
const isEdit = ref(false)

const rules: FormRules = {
  name: [
    { required: true, message: '请输入工具名称', trigger: 'blur' },
    { max: 50, message: '名称不超过 50 个字符', trigger: 'blur' }
  ],
  link: [
    { required: true, message: '请输入链接', trigger: 'blur' },
    {
      // 与后端 ToolSaveDTO 的校验保持一致：要么是 http(s) 外链，要么是 / 开头的站内路径
      pattern: /^(https?:\/\/|\/).+/,
      message: '链接需以 http:// 、https:// 或 / 开头',
      trigger: 'blur'
    }
  ],
  category: [{ max: 30, message: '分类不超过 30 个字符', trigger: 'blur' }],
  sort: [{ type: 'number', message: '排序必须是数字', trigger: 'blur' }]
}

/**
 * 弹窗打开时重置表单。
 *
 * 用 watch 监听 visible 而不是在父组件里手动调 reset，
 * 是为了让组件自己管好自己的状态——父组件只需要控制显隐。
 * immediate 不需要，因为初始时 visible 必然是 false。
 */
watch(
  () => props.visible,
  (visible) => {
    if (!visible) return

    // 每次打开都先清空校验痕迹，否则会残留上一次的红字
    formRef.value?.clearValidate()

    if (props.tool) {
      isEdit.value = true
      Object.assign(form.value, {
        name: props.tool.name,
        description: props.tool.description ?? '',
        icon: props.tool.icon ?? '',
        link: props.tool.link,
        category: props.tool.category,
        sort: props.tool.sort,
        status: props.tool.status
      })
    } else {
      isEdit.value = false
      Object.assign(form.value, emptyForm())
    }
  }
)

/**
 * 提交。
 *
 * 提交体里把空字符串的可选字段去掉，让后端收到 null 而不是 ""，
 * 避免数据库里存一堆空串（查询和展示时还要额外判断）。
 */
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  const payload: ToolPayload = {
    name: form.value.name.trim(),
    link: form.value.link.trim(),
    category: form.value.category.trim() || undefined,
    description: form.value.description.trim() || undefined,
    icon: form.value.icon.trim() || undefined,
    sort: form.value.sort,
    status: form.value.status
  }

  submitting.value = true
  try {
    if (isEdit.value && props.tool) {
      await updateTool(props.tool.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createTool(payload)
      ElMessage.success('新增成功')
    }
    emit('update:visible', false)
    emit('saved')
  } catch {
    // 业务错误（如 2002 重名）已由拦截器提示，弹窗保持打开让用户修改
  } finally {
    submitting.value = false
  }
}

/** 关闭弹窗，交给父组件控制 v-model */
function handleClose(): void {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑工具' : '新增工具'"
    :width="isMobile ? '92%' : '560px'"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :label-width="isMobile ? 'auto' : '72px'"
      :label-position="isMobile ? 'top' : 'right'"
    >
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="如：JSON 格式化" maxlength="50" show-word-limit />
      </el-form-item>

      <el-form-item label="分类" prop="category">
        <el-input v-model="form.category" placeholder="如：开发工具，留空则归入默认" maxlength="30" />
      </el-form-item>

      <el-form-item label="链接" prop="link">
        <el-input v-model="form.link" placeholder="https://... 或 /tools/xxx" />
      </el-form-item>

      <el-form-item label="图标">
        <el-input v-model="form.icon" placeholder="填一个 emoji，如 🧰" maxlength="8" />
      </el-form-item>

      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="一句话说明这个工具做什么"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="排序">
        <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
        <span class="field-hint">数字越小越靠前</span>
      </el-form-item>

      <el-form-item label="状态">
        <el-switch
          v-model="form.status"
          :active-value="1"
          :inactive-value="0"
          active-text="上架"
          inactive-text="下架"
          inline-prompt
        />
        <span class="field-hint">下架后展示页不可见</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.field-hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--app-text-secondary);
}
</style>
