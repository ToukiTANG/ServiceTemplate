package com.touki.storage.dto;

import com.touki.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 上传记录列表的查询条件（管理端用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UploadQueryDTO extends PageQuery {

    /** 文件名，模糊匹配 */
    private String fileName;

    /** 状态：0 上传中 / 1 可用 / 2 已删除，null 为全部 */
    private Integer status;
}
