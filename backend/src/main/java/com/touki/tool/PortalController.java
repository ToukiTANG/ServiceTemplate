package com.touki.tool;

import com.touki.common.R;
import com.touki.tool.vo.PortalToolVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 展示端接口（公开）。
 *
 * <p>路径前缀 {@code /api/portal/**} 在拦截器白名单里，不需要令牌。
 * 这里的返回值会暴露给所有访问者，新增字段前先确认不含内部信息。
 *
 * <p>只有查询，没有写操作——管理功能一律放在 {@link ToolController} 下。
 * 这种"读写分离到不同前缀"的结构，让权限边界变得一目了然：
 * 看到 {@code /api/portal} 就知道是公开的，看到 {@code /api/admin} 就知道要登录。
 */
@Tag(name = "展示端", description = "公开接口，无需登录")
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalController {

    private final ToolService toolService;

    /**
     * 查询所有上架工具。
     *
     * <p>一次返回全量，不分页、不按分类拆接口：
     * 工具总量很小（几十条），前端拿到后按 {@code category} 分组渲染即可。
     * 这样前端只需要一个请求就能画完整个页面。
     *
     * <p>返回顺序即渲染顺序：先按分类名升序，再按 sort 升序。
     *
     * @return 上架工具列表
     */
    @Operation(summary = "获取所有上架工具", description = "无需登录；按分类、排序值升序返回")
    @GetMapping("/tools")
    public R<List<PortalToolVO>> tools() {
        return R.ok(toolService.listVisible());
    }
}
