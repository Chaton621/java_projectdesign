@echo off
chcp 65001 >nul 2>&1
cls
echo ========================================
echo Force Reset User Data
echo ========================================
echo.
echo This script will:
echo 1. Delete all users except admin, huang, wen and their borrow records
echo 2. Generate 150 new users (user001-user150)
echo 3. Generate 20-50 borrow records per user (total ~4500-7500 records)
echo 4. Set reasonable fine amounts
echo.
echo WARNING: This will delete all user data except admin, huang, wen!
echo.
set /p confirm="Continue? (Y/N): "
if /i not "%confirm%"=="Y" (
    echo Operation cancelled.
    pause
    exit /b 0
)

echo.
echo [Step 1/3] Checking current data...
echo.

REM Database configuration
set DB_HOST=localhost
set DB_PORT=5432
set DB_NAME=library_db
set DB_USER=postgres
set DB_PASSWORD=20060201

REM Check if psql command is available
where psql >nul 2>&1
if errorlevel 1 (
    echo [ERROR] psql command not found!
    echo Please ensure PostgreSQL is installed and added to system PATH
    pause
    exit /b 1
)

REM Set PGPASSWORD environment variable
set PGPASSWORD=%DB_PASSWORD%

echo [INFO] Checking data before reset...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -P pager=off -f "%~dp0database\check_before_reset.sql"

echo.
echo [Step 2/3] Executing reset script...
echo.

psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -P pager=off -f "%~dp0database\reset_and_generate_users.sql" > reset_output.log 2>&1
set SQL_EXIT_CODE=%ERRORLEVEL%

REM Simple and reliable check: if COMMIT exists, script succeeded
REM COMMIT only appears when transaction completes successfully
findstr /C:"COMMIT" reset_output.log >nul 2>&1
if errorlevel 1 (
    REM No COMMIT found - check for actual errors
    REM Look for ROLLBACK which indicates transaction was rolled back
    findstr /C:"ROLLBACK" reset_output.log >nul 2>&1
    if errorlevel 0 (
        echo.
        echo [ERROR] SQL script execution failed - Transaction rolled back!
        echo Please check reset_output.log for details.
        echo.
        pause
        exit /b 1
    )
    
    REM No COMMIT and no ROLLBACK - script may have been interrupted
    echo.
    echo [ERROR] SQL script execution failed - COMMIT not found!
    echo This indicates the script did not complete successfully.
    echo Please check reset_output.log for details.
    echo.
    pause
    exit /b 1
)

REM COMMIT found - script succeeded!
echo.
echo [SUCCESS] User data reset completed!
echo.
echo Generated 150 new users:
echo - Username: user001 to user150
echo - Password: 12345
echo - Each user has 20-50 borrow records
echo - Total: ~4500-7500 borrow records
echo - huang: 30 borrow records, fine: 36.00 yuan
echo - wen: 35 borrow records, fine: 37.00 yuan
echo.

REM Clear password environment variable
set PGPASSWORD=

echo [Step 3/3] Verifying results...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -P pager=off -c "SELECT COUNT(*) as total_users, COUNT(*) FILTER (WHERE username IN ('admin', 'huang', 'wen')) as preserved, COUNT(*) FILTER (WHERE username LIKE 'user%%') as new_users FROM users;"

echo.
pause
