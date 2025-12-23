-- ========================================
-- 更新演示账户密码
-- 此脚本需要先运行Java程序生成密码哈希
-- ========================================
-- 
-- 运行以下命令生成密码哈希：
-- cd server
-- mvn compile
-- java -cp "target/classes;target/dependency/*" com.library.server.util.GenerateDemoData
--
-- 或者使用以下Java代码生成：
-- String passwordHash = PasswordUtil.hashPassword("12345");
-- System.out.println(passwordHash);
--
-- 然后将生成的哈希值替换下面的占位符

-- 更新huang的密码（密码: 12345）
-- UPDATE users SET password_hash = '<生成的密码哈希>' WHERE username = 'huang';

-- 更新wen的密码（密码: 12345）
-- UPDATE users SET password_hash = '<生成的密码哈希>' WHERE username = 'wen';










