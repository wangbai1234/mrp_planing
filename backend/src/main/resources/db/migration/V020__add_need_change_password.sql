-- V020: Add need_change_password flag and NEW_USER role for Feishu login

-- 用户表添加强制改密标志
ALTER TABLE `user` ADD COLUMN `need_change_password` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否需要修改密码(飞书新用户)';

-- 创建 NEW_USER 角色（无任何权限）
INSERT IGNORE INTO `role` (`code`, `name`, `description`) VALUES
    ('NEW_USER', '新建飞书用户', '飞书首次登录用户，需要管理员分配权限');