package com.touki.transfer;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.touki.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 文件传输记录，对应表 {@code transfer_file}。
 *
 * <p>一次"上传 → 生成提取码 → 下载 → 过期清理"的完整生命周期由这条记录承载。
 */
@Getter
@Setter
@ToString(callSuper = true, exclude = "storagePath")
@TableName("transfer_file")
public class TransferFile extends BaseEntity {

    /** 状态：上传中。分片还没传完，提取页不可见 */
    public static final int STATUS_UPLOADING = 0;

    /** 状态：可用。已合并完成，可以下载 */
    public static final int STATUS_READY = 1;

    /** 状态：已删除。文件已被清理任务或手动删除 */
    public static final int STATUS_DELETED = 2;

    /** 主键，雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 原始文件名，下载时会作为 Content-Disposition 的文件名 */
    private String fileName;

    /** 文件字节数 */
    private Long fileSize;

    /**
     * 客户端上传前生成的<b>弱指纹</b>，不是 SHA-256。
     *
     * <p>为什么不存真实摘要：500MB 的文件在前端算 SHA-256 需要把整个文件读进内存
     * （Web Crypto 不支持流式），浏览器会直接崩。所以前端用
     * 「文件名 + 大小 + 最后修改时间」拼一个指纹，同一个文件重传时能对上，
     * 就能找回上次传了一半的分片。
     *
     * <p>完整性由两道校验保证：合并前逐片校验大小、合并后校验总大小。
     * <b>不要</b>拿它和 SHA-256 比对——第一版就是这么写的，导致所有前端上传都失败。
     */
    private String fileHash;

    /** MIME 类型 */
    private String contentType;

    /**
     * 相对存储路径，如 {@code 20261107/1234567890.bin}。
     *
     * <p>只存相对路径，不存绝对路径：换机器、换挂载点时只改配置，不用刷数据。
     * 拼接绝对路径时必须校验，防止路径穿越。
     */
    private String storagePath;

    /** 6 位提取码，对方凭这个码下载 */
    private String code;

    /** 过期时间，到点后由清理任务删除文件并标记记录 */
    private LocalDateTime expireTime;

    /** 下载次数，每次成功下载 +1 */
    private Integer downloadCount;

    /** 状态：0 上传中 / 1 可用 / 2 已删除 */
    private Integer status;
}
