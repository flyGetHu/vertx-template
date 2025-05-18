-- 创建用户表
CREATE TABLE
  IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_active (active)
  );

-- 插入测试数据
INSERT INTO
  users (username, password, email, active)
VALUES
  (
    'admin',
    '$2a$10$hKDVYxLefVHV/vV76kCRKO6Kj2jlM3W0BQq7RJeLAhHWGEVVGj0ZC',
    'admin@example.com',
    TRUE
  ),
  (
    'user1',
    '$2a$10$q8//j8Lx7r4jSN6kuP7GJuZnX5ShN0N0kN.XshJPQZ6x9TmmbkRhS',
    'user1@example.com',
    TRUE
  ),
  (
    'user2',
    '$2a$10$q8//j8Lx7r4jSN6kuP7GJuZnX5ShN0N0kN.XshJPQZ6x9TmmbkRhS',
    'user2@example.com',
    FALSE
  );

-- 创建其他表，如需要
