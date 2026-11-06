package com.touki;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类。
 *
 * <p>启动后提供两类接口：
 * <ul>
 *   <li>{@code /api/portal/**}：展示端，完全公开，无需登录</li>
 *   <li>{@code /api/admin/**}：管理端，需携带 JWT（由 JwtInterceptor 拦截）</li>
 * </ul>
 *
 * <p>关于 Mapper 扫描：本项目不为 Mapper 单独建包，而是与业务类平铺在同一模块包下，
 * 因此不使用 {@code @MapperScan} 统一扫描（那会连普通接口一起扫进来）。
 * 每个 Mapper 接口自行标注 {@code @Mapper}，一目了然，新增模块也不用改启动类。
 */
@SpringBootApplication
public class TemplateApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemplateApplication.class, args);
    }
}
