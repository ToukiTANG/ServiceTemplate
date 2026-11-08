package com.touki.transfer.dto;

import com.touki.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 传输记录列表的查询条件（管理端）。
 *
 * <p>改造后去掉了「按文件名搜索」：文件名存在关联的 {@code upload_file} 表里，
 * 跨表模糊搜索要 join，为这点便利不值得。需要找某个文件时按提取码或时间找更快。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransferQueryDTO extends PageQuery {

    /** 状态：1 可用 / 2 已失效，null 为全部 */
    private Integer status;
}
