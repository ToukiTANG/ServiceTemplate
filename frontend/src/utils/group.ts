import type { PortalTool } from '@/types'

/** 一个分类下的所有工具 */
export interface ToolGroup {
  category: string
  items: PortalTool[]
}

/**
 * 按分类把工具列表分组。
 *
 * 用 Map 而不是一个普通对象来累积，是为了**保证分类的先后顺序稳定**。
 * 普通对象虽然也能保持插入顺序，但一旦分类名是纯数字字符串（比如 "2024"），
 * JS 引擎会把整数键提前排序，卡片顺序就会莫名其妙地变。Map 没有这个问题。
 *
 * 后端返回的列表已按 (category, sort) 升序排好，所以直接顺序遍历即可，
 * 分组后组内顺序和组间顺序都与后端一致，前端不需要再排序。
 *
 * @param tools 后端返回的上架工具列表
 * @returns 分组数组，数组顺序即渲染顺序
 */
export function groupByCategory(tools: PortalTool[]): ToolGroup[] {
  const map = new Map<string, PortalTool[]>()

  for (const tool of tools) {
    // 分类为空时兜底到「默认」，与后端的 normalizeCategory 保持一致
    const key = tool.category || '默认'
    const group = map.get(key)
    if (group) {
      group.push(tool)
    } else {
      map.set(key, [tool])
    }
  }

  return Array.from(map, ([category, items]) => ({ category, items }))
}

/**
 * 判断一个工具链接是否为外链。
 *
 * 与后端 ToolSaveDTO 的校验规则严格对应：
 * http:// 或 https:// 开头为外链，/ 开头为站内路径。
 *
 * @param link 工具链接
 */
export function isExternalLink(link: string): boolean {
  return link.startsWith('http://') || link.startsWith('https://')
}
