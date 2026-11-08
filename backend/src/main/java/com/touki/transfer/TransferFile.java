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
 * <p>改造后这张表只存<b>业务属性</b>：提取码、有效期、下载次数。
 * 文件名、大小、存储路径都在 {@code upload_file} 里——那是脚手架的职责，
 * 业务不该复制一份，否则改名或迁移时会出现两处真相。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("transfer_file")
public class TransferFile extends BaseEntity {

    /** 状态：可下载 */
    public static final int STATUS_READY = 1;

    /** 状态：已失效（过期或被手动删除） */
    public static final int STATUS_EXPIRED = 2;

    /** 主键，雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的文件上传记录 ID，指向 {@code upload_file.id} */
    private Long uploadFileId;

    /** 6 位提取码，对方凭这个码下载 */
    private String code;

    /** 过期时间，到点后清理任务删除对象与记录 */
    private LocalDateTime expireTime;

    /** 下载次数 */
    private Integer downloadCount;

    /** 状态：1 可用 / 2 已失效 */
    private Integer status;
}
