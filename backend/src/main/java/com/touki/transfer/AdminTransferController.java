package com.touki.transfer;

import com.touki.common.PageResult;
import com.touki.common.R;
import com.touki.transfer.dto.TransferQueryDTO;
import com.touki.transfer.vo.TransferAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件传输管理接口（管理端）。
 *
 * <p>路径前缀 {@code /api/admin/**}，自动受拦截器保护。
 */
@Tag(name = "文件传输管理", description = "查看与删除传输记录")
@RestController
@RequestMapping("/api/admin/transfers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminTransferController {

    private final TransferService transferService;

    /**
     * 分页查询传输记录。
     *
     * @param query 状态筛选与分页参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询传输记录", description = "文件名来自关联的上传记录，可按状态筛选")
    @GetMapping
    public R<PageResult<TransferAdminVO>> page(@Valid TransferQueryDTO query) {
        return R.ok(transferService.page(query));
    }

    /**
     * 删除传输记录，同时删除对象存储上的文件。
     *
     * @param id 记录 ID
     * @return 空响应
     */
    @Operation(summary = "删除传输记录", description = "同时删除对象存储上的文件，不可恢复")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        transferService.remove(id);
        return R.ok();
    }
}
