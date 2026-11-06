package com.touki.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表数据访问。
 *
 * <p>继承 {@code BaseMapper} 即获得单表增删改查，不需要写 XML。
 * 标注 {@code @Mapper} 让 MyBatis 自动注册——本项目没有为 Mapper 单独建包，
 * 因此不使用启动类上的 {@code @MapperScan}（那会连普通接口一起扫进来）。
 *
 * <p>当前只有一张单表、几条简单查询，全部用 Wrapper 构造即可，
 * 所以没有对应的 XML 文件。真需要多表关联或复杂统计时再建同名 XML，
 * 放到 {@code resources/mapper/} 下（需要在 application.yml 里配 mapper-locations）。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
