package com.touki.transfer;

import com.touki.common.R;
import com.touki.transfer.dto.InitUploadDTO;
import com.touki.transfer.vo.TransferFileVO;
import com.touki.transfer.vo.UploadCompleteVO;
import com.touki.transfer.vo.UploadInitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件传输接口。
 *
 * <p>路径刻意分成两组，靠前缀区分是否需要登录：
 * <ul>
 *   <li>{@code /api/transfer/upload/**}：<b>需要登录</b>。上传是要占磁盘的，
 *       公网环境下不能让人匿名往服务器塞文件。</li>
 *   <li>{@code /api/transfer/info/**} 与 {@code /api/transfer/download/**}：<b>公开</b>。
 *       对方收到提取码时通常没有账号，凭码就能查和下载。</li>
 * </ul>
 * 这两个公开前缀在 {@code WebConfig} 的白名单里。
 */
@Tag(name = "文件传输", description = "上传生成提取码，凭码下载")
@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    /**
     * 初始化上传。
     *
     * @param dto 文件元信息
     * @return uploadId 与已上传分片
     */
    @Operation(summary = "初始化上传", description = "需登录。返回 uploadId，断点续传时会带上已传完的分片序号")
    @PostMapping("/upload/init")
    public R<UploadInitVO> init(@Valid @RequestBody InitUploadDTO dto) {
        return R.ok(transferService.init(dto));
    }

    /**
     * 上传一个分片。
     *
     * <p>用 multipart 提交，{@code uploadId} 和 {@code index} 走 query 参数。
     *
     * @param uploadId 上传会话 ID
     * @param index    分片序号，从 0 开始
     * @param file     分片内容
     * @return 空响应
     */
    @Operation(summary = "上传分片", description = "需登录。按 index 从 0 开始逐个上传，可并发但建议不超过 3 个")
    @PostMapping(value = "/upload/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> uploadChunk(@RequestParam String uploadId,
                               @RequestParam int index,
                               @RequestParam("file") MultipartFile file) {
        transferService.uploadChunk(uploadId, index, file);
        return R.ok();
    }

    /**
     * 完成上传。
     *
     * @param uploadId 上传会话 ID
     * @return 提取码与有效期
     */
    @Operation(summary = "完成上传", description = "需登录。合并分片并校验，成功后返回提取码")
    @PostMapping("/upload/complete/{uploadId}")
    public R<UploadCompleteVO> complete(@PathVariable String uploadId) {
        return R.ok(transferService.complete(uploadId));
    }

    /**
     * 查询文件信息。
     *
     * @param code 提取码
     * @return 文件名、大小、过期时间
     */
    @Operation(summary = "查询文件信息", description = "公开接口，凭提取码查询")
    @GetMapping("/info/{code}")
    public R<TransferFileVO> info(@PathVariable String code) {
        return R.ok(transferService.getInfo(code));
    }

    /**
     * 下载文件。
     *
     * <p>返回文件流，不套 {@code R<T>}。
     *
     * @param code     提取码
     * @param response HTTP 响应
     */
    @Operation(summary = "下载文件", description = "公开接口，凭提取码下载；直接返回文件流")
    @GetMapping("/download/{code}")
    public void download(@PathVariable String code, HttpServletResponse response) {
        transferService.download(code, response);
    }
}
