package com.touki.storage;

import com.touki.common.PageResult;
import com.touki.common.R;
import com.touki.storage.dto.UploadQueryDTO;
import com.touki.storage.vo.UploadFileVO;
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
 * 上传记录管理接口（管理端）。
 *
 * <p>主要用途是排查孤儿文件：某条记录一直是「上传中」，说明那次上传没成功但留下了痕迹；
 * 或者文件已经没用了，在这里删掉（会连同对象存储上的文件一起删）。
 *
 * <p>业务侧引用文件的逻辑不在这里——业务表通过 {@code upload_file_id} 关联，各自管理自己的数据。
 */
@Tag(name = "上传记录管理", description = "查看与清理上传记录，含对象存储上的文件")
@RestController
@RequestMapping("/api/admin/uploads")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminUploadController {

    private final UploadService uploadService;

    /**
     * 分页查询上传记录。
     *
     * @param query 文件名模糊 + 状态筛选
     * @return 分页结果
     */
    @Operation(summary = "分页查询上传记录")
    @GetMapping
    public R<PageResult<UploadFileVO>> page(@Valid UploadQueryDTO query) {
        return R.ok(uploadService.page(query));
    }

    /**
     * 删除上传记录，同时删除对象存储上的文件。
     *
     * @param id 记录 ID
     * @return 空响应
     */
    @Operation(summary = "删除上传记录", description = "同时删除对象存储上的文件，不可恢复")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        uploadService.remove(id);
        return R.ok();
    }
}
