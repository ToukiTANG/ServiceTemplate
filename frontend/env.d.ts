/// <reference types="vite/client" />

/**
 * 让 TypeScript 认识 .vue 文件。
 *
 * 没有这段声明时，import 一个 .vue 组件会被 TS 判为"找不到模块"，
 * 虽然 Node 侧能跑（Vite 会处理），但编辑器和构建时的类型检查会全线报红。
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}
