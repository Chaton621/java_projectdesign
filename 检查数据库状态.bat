@echo off
chcp 65001 >nul
echo ========================================
echo 数据库状态检查脚本
echo ========================================
echo.

echo 此脚本将检查数据库状态，并提示需要执行的SQL脚本
echo.
echo 请确保PostgreSQL数据库正在运行，并且已创建数据库: library_db
echo.
pause

echo.
echo ========================================
echo 检查步骤：
echo ========================================
echo.
echo 1. 检查数据库连接
echo 2. 检查表是否存在
echo 3. 检查是否有数据
echo 4. 检查是否缺少字段（fine_amount等）
echo.
echo ========================================
echo 需要执行的SQL脚本（按顺序）：
echo ========================================
echo.
echo [1] database/schema.sql - 创建基础表结构
echo [2] database/add_fine_support.sql - 添加欠费支持字段
echo [3] database/add_chat_support.sql - 添加聊天支持
echo [4] database/add_fine_rate_config.sql - 添加梯度价格配置表
echo [5] database/init_admin.sql - 初始化管理员账户（或使用Java程序自动初始化）
echo [6] database/init_fine_rate_config.sql - 初始化梯度价格配置
echo [7] database/create_demo_users.sql - 创建演示用户和数据（可选）
echo [8] database/import_demo_data.sql - 导入演示图书数据（可选）
echo.
echo ========================================
echo 重要提示：
echo ========================================
echo.
echo 1. 如果数据库是新建的，需要按顺序执行上述SQL脚本
echo 2. 如果数据库已存在，只需要执行缺失的脚本
echo 3. 管理员账户会在服务器启动时自动创建（如果不存在）
echo 4. 梯度价格配置会在服务器启动时自动初始化（如果不存在）
echo.
echo ========================================
echo 快速检查方法：
echo ========================================
echo.
echo 使用psql连接到数据库，执行以下SQL：
echo.
echo -- 检查表是否存在
echo SELECT table_name FROM information_schema.tables 
echo WHERE table_schema = 'public' 
echo AND table_name IN ('users', 'books', 'borrow_records', 'fine_rate_config');
echo.
echo -- 检查users表是否有fine_amount字段
echo SELECT column_name FROM information_schema.columns 
echo WHERE table_name = 'users' AND column_name = 'fine_amount';
echo.
echo -- 检查是否有数据
echo SELECT COUNT(*) as user_count FROM users;
echo SELECT COUNT(*) as book_count FROM books;
echo SELECT COUNT(*) as record_count FROM borrow_records;
echo SELECT COUNT(*) as config_count FROM fine_rate_config;
echo.
echo ========================================
echo 如果表是空的，可以：
echo ========================================
echo.
echo 1. 执行 database/init_fine_rate_config.sql 初始化梯度价格
echo 2. 启动服务器，会自动创建管理员账户
echo 3. 执行 database/create_demo_users.sql 创建演示数据（可选）
echo 4. 执行 database/import_demo_data.sql 导入图书数据（可选）
echo.
pause
