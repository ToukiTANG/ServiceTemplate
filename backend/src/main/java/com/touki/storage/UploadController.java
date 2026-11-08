package com.touki.storage;

import com.touki.common.R;
import com.touki.storage.dto.PrepareUploadDTO;
import com.touki.storage.dto.UploadCredentialDTO;
import com.touki.storage.vo.PrepareUploadVO;
import com.touki.storage.vo.UploadCredentialVO;
import com.touki.storage.vo.UploadFileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件上传接口。
 *
 * <p>这三步都不传输文件内容——文件由前端直接 PUT 到对象存储。
 * 服务器只交换元数据和签名，所以接口非常轻。
 *
 * <p>全部路径都在 {@code /api/upload/**} 下，受拦截器保护（必须登录）。
 * 这是刻意的：签名能力如果匿名可用，等于把对象存储的写权限拱手让人。
 */
@Tag(name = "文件上传", description = "直传对象存储的三步：登记 → 签名 → 确认")
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UploadController {

    private final UploadService uploadService;

    /**
     * 第一步：登记文件信息，拿到存储路径。
     *
     * @param dto 文件名、大小、类型
     * @return 上传 ID、存储路径、分片大小
     */
    @Operation(summary = "登记上传",
            description = "校验大小并分配存储路径。拿到 storageKey 后开始直传")
    @PostMapping("/prepare")
    public R<PrepareUploadVO> prepare(@Valid @RequestBody PrepareUploadDTO dto) {
        return R.ok(uploadService.prepare(dto));
    }

    /**
     * 第二步：为一次上传请求签名。
     *
     * <p>每个分片都要调一次——签名里包含请求长度，不同分片长度不同。
     *
     * @param dto 请求方法、路径、内容长度
     * @return 请求头需要的授权信息
     */
    @Operation(summary = "获取上传签名",
            description = "每个分片调一次；返回的 Authorization 与 Date 必须原样带到对象存储请求里")
    @PostMapping("/credential")
    public R<UploadCredentialVO> credential(@Valid @RequestBody UploadCredentialDTO dto) {
        return R.ok(uploadService.credential(dto));
    }

    /**
     * 第三步：标记上传完成。
     *
     * <p>服务端会调用又拍云合并分片，所以必须带上 prepare 时拿到的 {@code multiUuid}。
     *
     * @param uploadId  上传记录 ID
     * @param multiUuid 分片任务标识
     * @return 文件信息（含访问 URL）
     */
    @Operation(summary = "确认上传完成", description = "全部数据传完后调用，服务端负责合并分片")
    @PostMapping("/complete/{uploadId}")
    public R<UploadFileVO> complete(@PathVariable Long uploadId,
                                    @RequestParam(required = false) String multiUuid) {
        return R.ok(uploadService.complete(uploadId, multiUuid));
    }
}
