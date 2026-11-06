package com.touki.tool;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具表数据访问。
 *
 * <p>继承 {@code BaseMapper} 即获得单表增删改查，全部用 Wrapper 构造，
 * 当前不需要 XML。真需要复杂关联查询时再建同名 XML，
 * 并注意 {@code application.yml} 里要配 {@code mybatis-plus.mapper-locations}
 * （默认只扫 {@code classpath*:/mapper/**\/*.xml}）。
 */
@Mapper
public interface ToolMapper extends BaseMapper<Tool> {
}
