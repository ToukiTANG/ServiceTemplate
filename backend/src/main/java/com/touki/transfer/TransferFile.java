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
     * 文件 SHA-256。
     *
     * <p>合并完成后计算并校验：前端在上传前也会算一遍，两边不一致说明传输过程中
     * 有分片损坏，此时必须报错并清理，不能留下一个"能下载但打不开"的文件。
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
