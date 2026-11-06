import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import './styles/main.css'

/**
 * 应用入口。
 *
 * 顺序有讲究：先装 pinia 再装 router。
 * 路由守卫里会用到 store（判断有没有登录），如果 pinia 还没挂载，
 * 守卫第一次执行时 useUserStore() 会抛「no active pinia」，表现为刷新页面白屏。
 *
 * 这里没有 `app.use(ElementPlus)`：组件和 API 都由 unplugin 按需引入，
 * 全量注册会让每个页面都带上整个组件库。
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
