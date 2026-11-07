package com.touki.transfer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件传输记录的数据访问。
 *
 * <p>单表操作，全部用 Wrapper 构造，不需要 XML。
 */
@Mapper
public interface TransferFileMapper extends BaseMapper<TransferFile> {
}
