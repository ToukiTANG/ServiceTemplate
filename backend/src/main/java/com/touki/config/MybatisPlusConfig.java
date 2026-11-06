package com.touki.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置。
 *
 * <p>只做两件事：开分页插件、配置审计字段自动填充。
 * 没有引入乐观锁、多租户、动态数据源等插件——本项目用不到，
 * 加了只会让每次 SQL 执行多一层拦截器判断。
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件。
     *
     * <p>必须配置，否则 {@code Page} 参数不会生效，MP 会把 {@code LIMIT} 当普通 SQL 拼进查询，
     * 表现为"查出来永远是全表"。这是接入 MP 时最容易漏的一步。
     *
     * @return 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限兜底。PageQuery 里已经截断过一次，这里再设一道，
        // 防止有人绕过分页 DTO 直接把 Page 传给 mapper
        pagination.setMaxLimit(200L);
        // 页码超过总页数时是否回到第一页：设 false，直接返回空列表更符合直觉
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }

    /**
     * 审计字段自动填充。
     *
     * <p>实体里 {@code createTime}、{@code updateTime} 都标注了
     * {@code @TableField(fill = ...)}，插入/更新时由这里统一赋值，
     * 业务代码不需要手写 {@code entity.setCreateTime(LocalDateTime.now())}。
     *
     * <p>为什么用 {@code strictInsertFill} / {@code strictUpdateFill} 而不是直接 set：
     * 前两者只在字段为 null 时才填充。这样如果有特殊场景需要手动指定时间
     * （比如导入历史数据），手动设置的值不会被覆盖。
     *
     * @return 字段填充处理器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                // 更新时间每次都要覆盖，所以用 setFieldValByName 而不是 strict 系列，
                // 否则字段已有旧值时就填不进去了
                setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
            }
        };
    }
}
