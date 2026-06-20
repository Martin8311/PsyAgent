package com.mindbridge.user;

/**
 * 用户角色。
 *
 * <p>STUDENT：学生，只能使用聊天功能；
 * ADMIN：管理员，可访问 /api/admin/** 后台。
 */
public enum Role {
    STUDENT,
    ADMIN
}
