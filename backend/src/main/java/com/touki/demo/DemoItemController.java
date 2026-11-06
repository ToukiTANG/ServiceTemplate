package com.touki.demo;

import com.touki.common.PageResult;
import com.touki.common.R;
import com.touki.demo.dto.DemoItemQueryDTO;
import com.touki.demo.dto.DemoItemSaveDTO;
import com.touki.demo.vo.DemoItemVO;
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
 * 示例接口（管理端）。
 *
 * <p><b>这是"照着抄"的模板。</b>新增一个业务模块时，把这个类连同 Service、DTO、VO
 * 一起复制一份改名即可，注意三件事：
 * <ol>
 *   <li>路径前缀改成自己的资源名，仍放在 {@code /api/admin/} 下（自动受拦截器保护）</li>
 *   <li>类上保留 {@code @SecurityRequirement}，Swagger 里才会带上令牌</li>
 *   <li>每个方法加 {@code @Operation(summary = "中文说明")}，别只写英文或干脆不写</li>
 * </ol>
 *
 * <p>方法体都在三行以内：收参数 → 调 Service → 包 {@code R}。
 * 一旦发现某个方法开始写 if 判断，说明业务逻辑放错地方了。
 */
@Tag(name = "示例数据", description = "演示完整的增删改查，业务开发前可整块删除")
@RestController
@RequestMapping("/api/admin/items")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DemoItemController {

    private final DemoItemService itemService;

    /**
     * 分页查询。
     *
     * @param query 查询条件与分页参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询", description = "条件均可选；name 为模糊匹配，status 为精确匹配")
    @GetMapping
    public R<PageResult<DemoItemVO>> page(@Valid DemoItemQueryDTO query) {
        return R.ok(itemService.page(query));
    }

    /**
     * 查询详情。
     *
     * @param id 主键
     * @return 详情
     */
    @Operation(summary = "查询详情")
    @GetMapping("/{id}")
    public R<DemoItemVO> detail(@PathVariable Long id) {
        return R.ok(itemService.detail(id));
    }

    /**
     * 新增。
     *
     * @param dto 入参
     * @return 新记录主键（序列化后前端收到字符串）
     */
    @Operation(summary = "新增", description = "名称不可重复")
    @PostMapping
    public R<Long> create(@Valid @RequestBody DemoItemSaveDTO dto) {
        return R.ok(itemService.create(dto));
    }

    /**
     * 修改。
     *
     * @param id  主键
     * @param dto 入参
     * @return 空响应
     */
    @Operation(summary = "修改")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DemoItemSaveDTO dto) {
        itemService.update(id, dto);
        return R.ok();
    }

    /**
     * 删除。
     *
     * @param id 主键
     * @return 空响应
     */
    @Operation(summary = "删除", description = "物理删除，不可恢复")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        itemService.remove(id);
        return R.ok();
    }

    /**
     * 启用 / 停用。
     *
     * <p>用 PATCH 而不是 PUT：只改一个字段，属于局部更新。
     *
     * @param id     主键
     * @param status 1 启用 / 0 停用
     * @return 空响应
     */
    @Operation(summary = "启用/停用", description = "status=1 启用，status=0 停用")
    @PatchMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        itemService.changeStatus(id, status);
        return R.ok();
    }
}
