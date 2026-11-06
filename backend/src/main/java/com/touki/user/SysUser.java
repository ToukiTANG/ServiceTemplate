package com.touki.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.touki.common.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 用户实体，对应表 {@code sys_user}。
 *
 * <p>本项目没有角色、权限、部门这些概念——单人使用，登录只是为了让"管理端"不被别人打开。
 * 如果将来需要更细的权限，在这里加 {@code role} 字段并扩展 JwtInterceptor 即可。
 *
 * <p>{@code @ToString(exclude = "password")} 是必须的：实体会被日志、调试器、
 * 异常信息打印，一旦把 BCrypt 密文打进日志，等于把密码泄露到日志文件里。
 */
@Getter
@Setter
@ToString(callSuper = true, exclude = "password")
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 主键，雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录名，唯一 */
    private String username;

    /**
     * 密码的 BCrypt 密文。
     *
     * <p>永远不要对明文赋值到这里。写入前必须经过 {@code passwordEncoder.encode(...)}，
     * 比对时用 {@code passwordEncoder.matches(明文, 密文)}。
     */
    private String password;

    /** 显示名 */
    private String nickname;

    /** 状态：1 正常 / 0 禁用。禁用后无法登录，已签发的令牌仍有效（单令牌方案的已知取舍） */
    private Integer status;
}
