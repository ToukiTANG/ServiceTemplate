package com.touki.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置。
 *
 * <p>访问地址 {@code http://localhost:8080/swagger-ui.html}。
 *
 * <p>不配置 {@code GroupedOpenApi} 分组：本项目接口量在二十个以内，
 * 全部平铺反而好找；分组适合接口上百个的场景。
 */
@Configuration
public class OpenApiConfig {

    /** 安全方案名称，被各 Controller 上的 {@code @SecurityRequirement(name = ...)} 引用 */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * 文档基础信息与认证方案。
     *
     * <p>这里定义的 {@code bearerAuth} 是 Swagger UI 右上角 Authorize 按钮的来源。
     * 少了这段配置，{@code @SecurityRequirement} 会指向一个不存在的方案，
     * Swagger UI 虽然不报错，但界面上不会出现填写令牌的地方，导致所有管理端接口
     * 在文档里都调不通。
     *
     * @return OpenAPI 元信息
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("服务脚手架模板 API")
                        .version("1.0.0")
                        .description("""
                                接口分为三组：

                                - **认证** `/api/auth/**`：登录接口公开，其余需要令牌
                                - **展示端** `/api/portal/**`：完全公开，无需登录
                                - **工具管理** `/api/admin/**`：需要登录

                                调试步骤：先调 `/api/auth/login`（admin / admin123）拿到 token，
                                点右上角 **Authorize** 粘贴进去，之后就能调管理端接口了。
                                """))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                // scheme=bearer 表示 "Authorization: Bearer {token}"，
                                // Swagger UI 会自动补前缀，所以 Authorize 弹窗里只填 token 本身
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 token，不要加 Bearer 前缀")));
    }
}
