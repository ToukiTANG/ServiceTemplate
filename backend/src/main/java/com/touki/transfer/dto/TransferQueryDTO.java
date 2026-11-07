package com.touki.transfer.dto;

import com.touki.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端传输列表的查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransferQueryDTO extends PageQuery {

    /** 文件名，模糊匹配 */
    private String fileName;

    /** 状态：0 上传中 / 1 可用 / 2 已删除，null 为全部 */
    private Integer status;
}
