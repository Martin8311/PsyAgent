package com.mindbridge.config;

import com.mindbridge.user.Role;
import com.mindbridge.user.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化种子账号，方便开发与演示。
 *
 * <ul>
 *   <li>admin / admin123     —— 管理员</li>
 *   <li>student / student123 —— 学生</li>
 * </ul>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private final UserService userService;

    public DataInitializer(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        userService.createIfAbsent("admin", "admin123", Role.ADMIN);
        userService.createIfAbsent("student", "student123", Role.STUDENT);
        // 给昵称为空的历史用户回填昵称=用户名
        userService.backfillNicknames();
    }
}
