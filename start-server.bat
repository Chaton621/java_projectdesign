@echo off
chcp 65001 >nul
cd /d %~dp0

set PORT=9090
if "%1" NEQ "" set PORT=%1

netstat -ano | findstr ":%PORT%" | findstr "LISTENING" >nul
if %ERRORLEVEL% EQU 0 (
    echo [错误] 端口 %PORT% 已被占用！
    echo.
    echo 占用该端口的进程信息：
    netstat -ano | findstr ":%PORT%" | findstr "LISTENING"
    echo.
    echo 解决方案：
    echo 1. 使用其他端口启动：start-server.bat [端口号]
    echo 2. 手动终止进程：打开任务管理器，找到对应的 Java 进程并结束
    echo.
    pause
    exit /b 1
)

echo 正在启动服务器，端口: %PORT%
echo.
call mvn -pl server compile exec:java -Dexec.mainClass="com.library.server.ServerMain" -Dexec.args="%PORT%" -Dexec.jvmArgs="-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8"
pause



