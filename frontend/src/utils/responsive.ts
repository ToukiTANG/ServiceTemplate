import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 监听一个 CSS 媒体查询是否命中。
 *
 * 为什么不用 window.innerWidth + resize 监听：
 * 1. resize 在手机上会随地址栏收放频繁触发，每次都要手动比较宽度
 * 2. matchMedia 的回调只在实际跨越断点时触发一次，开销小得多
 * 3. 断点定义在 CSS 里（media query 字符串）和 JS 里是同一个值，不容易写歪
 *
 * 注意：初始值在 onMounted 里才读取。首屏渲染时会先按 false（桌面）画一帧，
 * 对于布局类切换这没什么影响；如果确实关心首屏闪烁，可以在 setup 顶层
 * 用 typeof window !== 'undefined' 判断后同步初始化，但本项目是纯 SPA，不需要。
 *
 * @param query 媒体查询字符串，例如 '(max-width: 767px)'
 * @returns 只读的响应式布尔值
 */
export function useMediaQuery(query: string) {
  const matches = ref(false)

  // 保存引用，卸载时才能准确移除监听器
  let mql: MediaQueryList | null = null

  const handleChange = (event: MediaQueryListEvent) => {
    matches.value = event.matches
  }

  onMounted(() => {
    mql = window.matchMedia(query)
    matches.value = mql.matches
    mql.addEventListener('change', handleChange)
  })

  onUnmounted(() => {
    mql?.removeEventListener('change', handleChange)
  })

  return matches
}

/**
 * 手机断点：小于 768px 视为手机。
 *
 * 768px 是 iPad 竖屏的宽度，再往上是平板/桌面。
 * 项目里所有"要不要切换到移动端布局"的判断都应该用这个函数，
 * 不要各自写魔法数字，否则将来调整断点要满仓库找。
 */
export function useIsMobile() {
  return useMediaQuery('(max-width: 767px)')
}
