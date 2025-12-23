-- 管理员账户初始化SQL
-- 默认用户名: admin
-- 默认密码: admin123
-- 
-- 注意：此SQL使用PBKDF2哈希算法生成的密码哈希
-- 如果使用不同的哈希算法，需要相应调整

-- 方法1：使用应用层生成的哈希（推荐）
-- 运行ServerMain时会自动调用AdminInitializer.initializeAdmin()

-- 方法2：手动插入（需要先运行Java程序生成密码哈希）
-- INSERT INTO users (username, password_hash, role, status, created_at)
-- VALUES ('admin', '<从AdminInitializer获取的哈希值>', 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP)
-- ON CONFLICT (username) DO NOTHING;

-- 方法3：使用PostgreSQL函数生成简单哈希（不推荐，仅用于测试）
-- 注意：这不是PBKDF2，仅用于快速测试
-- INSERT INTO users (username, password_hash, role, status, created_at)
-- VALUES ('admin', '100000:' || encode(digest('admin123', 'sha256'), 'base64') || ':' || encode(digest('admin123', 'sha256'), 'base64'), 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP)
-- ON CONFLICT (username) DO NOTHING;

-- 验证管理员账户
SELECT id, username, role, status FROM users WHERE username = 'admin';



















