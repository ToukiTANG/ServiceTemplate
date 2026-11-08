package com.touki.transfer;

import com.touki.common.R;
import com.touki.transfer.dto.TransferCreateDTO;
import com.touki.transfer.vo.TransferFileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件传输接口。
 *
 * <p>改造后不再接收文件——文件由前端通过脚手架的 {@code /api/upload/*} 直传对象存储。
 * 本模块只负责"把已上传的文件包装成可分享的提取码"。
 *
 * <p>路径分两组，靠前缀区分是否需要登录：
 * <ul>
 *   <li>{@code /create}：<b>需登录</b>。生成分享记录是要写库的，不能让匿名用户刷</li>
 *   <li>{@code /info/**}、{@code /download/**}：<b>公开</b>。收到提取码的人通常没有账号</li>
 * </ul>
 */
@Tag(name = "文件传输", description = "为已上传的文件生成提取码，凭码下载")
@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    /**
     * 创建传输记录。
     *
     * @param dto 文件上传记录 ID 与期望有效期
     * @return 提取码与直链
     */
    @Operation(summary = "创建传输",
            description = "需登录。传入 /api/upload/complete 返回的文件 ID，生成 6 位提取码")
    @PostMapping("/create")
    @SecurityRequirement(name = "bearerAuth")
    public R<TransferFileVO> create(@Valid @RequestBody TransferCreateDTO dto) {
        return R.ok(transferService.create(dto));
    }

    /**
     * 查询文件信息。
     *
     * @param code 提取码
     * @return 文件名、大小、直链、过期时间
     */
    @Operation(summary = "查询文件信息", description = "公开接口，凭提取码查询")
    @GetMapping("/info/{code}")
    public R<TransferFileVO> info(@PathVariable String code) {
        return R.ok(transferService.getInfo(code));
    }

    /**
     * 下载：302 重定向到对象存储直链。
     *
     * <p>不返回文件流——那样几百 MB 流量会走服务器，把带宽吃满。
     *
     * @param code     提取码
     * @param response HTTP 响应
     */
    @Operation(summary = "下载文件",
            description = "公开接口。返回 302 重定向到对象存储，浏览器直接从 CDN 下载")
    @GetMapping("/download/{code}")
    public void download(@PathVariable String code, HttpServletResponse response) {
        transferService.download(code, response);
    }
}
