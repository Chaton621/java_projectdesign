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
echo [INFO] Attempting to clean project...
call mvn clean compile
if errorlevel 1 (
    echo [WARNING] Clean failed, trying to close file handles...
    echo [INFO] Attempting to unlock files...
    REM Try to close any processes that might be locking the files
    taskkill /F /IM javaw.exe >nul 2>&1
    timeout /t 1 >nul 2>&1
    echo [INFO] Retrying compilation without clean...
    call mvn compile
    if errorlevel 1 (
        echo [ERROR] Compilation failed!
        echo Possible causes:
        echo 1. Code errors
        echo 2. Missing dependencies
        echo 3. Files are locked (close IDE or image viewers)
        echo.
        echo Solution: Close all programs that might be using the files, then try again.
        pause
        exit /b 1
    ) else (
        echo [SUCCESS] Compilation completed (clean step skipped)
    )
) else (
    echo [SUCCESS] Clean and compilation completed
)

echo.
echo [Step 3/3] Compilation completed!
echo.
echo Now you can:
echo 1. Run start-server.bat to start the server
echo 2. Run start-client.bat to start the client
echo.
pause