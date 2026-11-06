<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'

/**
 * 根组件。
 *
 * 只做两件事：注入中文语言包、渲染路由出口。
 *
 * 为什么用 el-config-provider 包一层而不是在 main.ts 里 app.use(ElementPlus, { locale })：
 * 按需引入模式下没有 ElementPlus 这个全局安装对象了，
 * ConfigProvider 是官方推荐的替代方式，只影响需要国际化的那几个组件
 * （分页器、日期选择器、空状态等）。
 */
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
