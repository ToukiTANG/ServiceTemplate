import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

/**
 * Vite 配置。
 *
 * 开发期通过 proxy 把 /api 转发到后端 8080，前端代码里只写相对路径，
 * 这样本地开发、部署（nginx 反代）用的是同一套路径，不需要切环境变量。
 *
 * 关于 Element Plus 的按需引入：
 * - Components 插件负责模板里出现的 `<el-xxx>` 组件，用到哪个才 import 哪个
 * - AutoImport 插件负责 JS 里直接使用的 API（ElMessage、ElMessageBox 等），
 *   所以业务代码里不需要再写 `import { ElMessage } from 'element-plus'`
 * - 两者都会顺带把对应组件的样式引进来，因此 main.ts 里不再需要
 *   全量的 `element-plus/dist/index.css`（那份是 360KB 左右）
 *
 * 两个 d.ts 是插件自动生成的，用来让 TypeScript 认识这些"凭空出现"的全局变量。
 * 它们应该提交到版本库，否则别人 clone 下来编辑器会满屏红波浪线。
 */
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts'
    })
  ],

  resolve: {
    alias: {
      // 用 @ 指向 src，避免出现 ../../../ 这种难以阅读的相对路径
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },

  server: {
    port: 5173,
    proxy: {
      // 所有 /api 开头的请求转发到后端，changeOrigin 让后端看到正确的 Host
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
