<script setup lang="ts">
import { ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { createItem, updateItem } from '@/api/item'
import type { DemoItem, DemoItemPayload } from '@/types'
import { useIsMobile } from '@/utils/responsive'

/**
 * 示例新增/编辑弹窗。
 *
 * <p>新增和编辑共用一个组件：靠 props.item 是否有 id 来区分模式。
 * 这样表单字段、校验规则只需要维护一份，不会出现
 * 「新增能填的字段编辑时漏了」这种问题。
 */

const props = defineProps<{
  /** 弹窗是否可见，配合 v-model:visible 使用 */
  visible: boolean
  /** 编辑时传入的记录；新增时为 null */
  item: DemoItem | null
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
 * 弹窗在手机上不能用固定宽度：375px 的屏幕会直接溢出。
 * 同时 label 从左侧改到顶部，否则两字标签会占掉一半可用宽度。
 */
const isMobile = useIsMobile()

/**
 * 表单模型。
 *
 * 与 DemoItemPayload 的区别是：这里所有字段都是必填的。
 * 原因是表单控件对类型很敏感——el-input 的 v-model 要 string，
 * 如果字段声明成可选（string | undefined），模板里每个 v-model 都会报类型错误。
 * 提交时再转成 payload，把空字符串统一转成 undefined。
 */
interface ItemFormModel {
  name: string
  status: number
  remark: string
}

/** 表单模型，初始值为一套空数据 */
function emptyForm(): ItemFormModel {
  return {
    name: '',
    status: 1,
    remark: ''
  }
}

const form = ref<ItemFormModel>(emptyForm())

/** 当前是否为编辑模式 */
const isEdit = ref(false)

const rules: FormRules = {
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { max: 50, message: '名称不能超过 50 个字符', trigger: 'blur' }
  ],
  remark: [{ max: 200, message: '备注不能超过 200 个字符', trigger: 'blur' }]
}

/**
 * 弹窗打开时重置表单。
 *
 * 用 watch 监听 visible 而不是让父组件手动调 reset，
 * 是为了让组件自己管好自己的状态——父组件只需要控制显隐。
 */
watch(
  () => props.visible,
  (visible) => {
    if (!visible) return

    // 每次打开都先清空校验痕迹，否则会残留上一次的红字
    formRef.value?.clearValidate()

    if (props.item) {
      isEdit.value = true
      form.value.name = props.item.name
      form.value.status = props.item.status
      form.value.remark = props.item.remark ?? ''
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

  const payload: DemoItemPayload = {
    name: form.value.name.trim(),
    status: form.value.status,
    remark: form.value.remark.trim() || undefined
  }

  submitting.value = true
  try {
    if (isEdit.value && props.item) {
      await updateItem(props.item.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createItem(payload)
      ElMessage.success('新增成功')
    }
    emit('update:visible', false)
    emit('saved')
  } catch {
    // 业务错误（如名称重复）已由拦截器提示，弹窗保持打开让用户修改
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
    :title="isEdit ? '编辑示例数据' : '新增示例数据'"
    :width="isMobile ? '92%' : '520px'"
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
        <el-input v-model="form.name" placeholder="请输入名称" maxlength="50" show-word-limit />
      </el-form-item>

      <el-form-item label="状态">
        <el-switch
          v-model="form.status"
          :active-value="1"
          :inactive-value="0"
          active-text="启用"
          inactive-text="停用"
          inline-prompt
        />
        <span class="field-hint">停用后在列表里显示灰色标签</span>
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          placeholder="可选的说明文字"
          maxlength="200"
          show-word-limit
        />
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
