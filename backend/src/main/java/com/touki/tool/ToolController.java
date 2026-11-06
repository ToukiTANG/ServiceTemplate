package com.touki.tool;

import com.touki.common.PageResult;
import com.touki.common.R;
import com.touki.tool.dto.ToolQueryDTO;
import com.touki.tool.dto.ToolSaveDTO;
import com.touki.tool.vo.ToolVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具管理接口（管理端）。
 *
 * <p>路径前缀 {@code /api/admin/**} 落在拦截器的保护范围内，所有方法都需要登录，
 * 因此类上统一声明了 {@code @SecurityRequirement}，不用每个方法重复写。
 *
 * <p>这里的每个方法都在三行以内：接参数 → 调 Service → 包 R。
 * 如果发现某个方法开始写 if 判断，说明业务逻辑放错地方了，应该挪到 Service。
 */
@Tag(name = "工具管理", description = "管理端接口，需要登录")
@RestController
@RequestMapping("/api/admin/tools")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ToolController {

    private final ToolService toolService;

    /**
     * 分页查询工具。
     *
     * @param query 查询条件（名称模糊、分类精确、状态）与分页参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询工具",
            description = "条件均可选；name 为模糊匹配，category 与 status 为精确匹配")
    @GetMapping
    public R<PageResult<ToolVO>> page(@Valid ToolQueryDTO query) {
        return R.ok(toolService.page(query));
    }

    /**
     * 查询工具详情。
     *
     * @param id 工具 ID
     * @return 工具详情
     */
    @Operation(summary = "查询工具详情")
    @GetMapping("/{id}")
    public R<ToolVO> detail(@PathVariable Long id) {
        return R.ok(toolService.detail(id));
    }

    /**
     * 新增工具。
     *
     * @param dto 工具信息
     * @return 新工具 ID（Long，序列化后前端收到的是字符串）
     */
    @Operation(summary = "新增工具", description = "同一分类下名称不可重复")
    @PostMapping
    public R<Long> create(@Valid @RequestBody ToolSaveDTO dto) {
        return R.ok(toolService.create(dto));
    }

    /**
     * 修改工具。
     *
     * @param id  工具 ID
     * @param dto 工具信息
     * @return 空响应
     */
    @Operation(summary = "修改工具")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ToolSaveDTO dto) {
        toolService.update(id, dto);
        return R.ok();
    }

    /**
     * 删除工具。
     *
     * @param id 工具 ID
     * @return 空响应
     */
    @Operation(summary = "删除工具", description = "物理删除，不可恢复")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        toolService.remove(id);
        return R.ok();
    }

    /**
     * 上架 / 下架。
     *
     * <p>用 PATCH 而不是 PUT：只改 status 一个字段，属于局部更新。
     *
     * @param id     工具 ID
     * @param status 1 上架 / 0 下架
     * @return 空响应
     */
    @Operation(summary = "上架/下架", description = "status=1 上架，status=0 下架")
    @PatchMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        toolService.changeStatus(id, status);
        return R.ok();
    }
}
