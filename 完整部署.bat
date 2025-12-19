@echo off
chcp 65001 >nul
echo ========================================
echo Full Deployment Script
echo ========================================
echo.

echo [Step 1/3] Database Migration...
echo Please use database management tool to execute SQL scripts:
echo   - database/schema.sql
echo   - database/add_fine_support.sql
echo   - database/add_chat_support.sql
echo   - database/add_fine_rate_config.sql
echo   - database/init_admin.sql
echo.
pause

echo.
echo [Step 2/3] Cleaning and compiling project...
echo 注意：如果清理失败，将自动尝试直接编译（跳过清理步骤）...
echo.

REM 先尝试清理和编译
call mvn clean compile
if errorlevel 1 (
    echo.
    echo ========================================
    echo [警告] 清理步骤失败
    echo ========================================
    echo.
    echo 可能原因：文件被其他程序占用（如IDE、Java进程等）
    echo 解决方案：跳过清理步骤，直接编译...
    echo.
    call mvn compile
    if errorlevel 1 (
        echo.
        echo ========================================
        echo [错误] 编译失败！
        echo ========================================
        echo.
        echo 可能的原因：
        echo 1. 代码存在编译错误
        echo 2. 依赖项缺失或配置错误
        echo 3. 文件被其他程序占用
        echo.
        echo 建议操作：
        echo - 关闭可能占用文件的程序（如IDE、其他Java进程）
        echo - 检查上方的错误信息并修复代码问题
        echo - 尝试手动运行: mvn compile
        echo.
        pause
        exit /b 1
    ) else (
        echo.
        echo [成功] 编译完成！（已跳过清理步骤）
    )
) else (
    echo.
    echo [成功] 清理和编译完成！
)

echo.
echo [Step 3/3] Compilation completed!
echo.
echo Now you can:
echo 1. Run start-server.bat to start the server
echo 2. Run start-client.bat to start the client (can run multiple times for multiple clients)
echo.
pause




