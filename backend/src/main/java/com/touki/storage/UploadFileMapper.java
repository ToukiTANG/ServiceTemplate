package com.touki.storage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上传记录的数据访问。
 *
 * <p>单表操作，全部用 Wrapper 构造，不需要 XML。
 */
@Mapper
public interface UploadFileMapper extends BaseMapper<UploadFile> {
}
