@echo off
chcp 65001 >nul
cd /d %~dp0

set PORT=9090
if "%1" NEQ "" set PORT=%1

netstat -ano | findstr ":%PORT%" | findstr "LISTENING" >nul
if %ERRORLEVEL% EQU 0 (
    echo [错误] 端口 %PORT% 已被占用
    netstat -ano | findstr ":%PORT%" | findstr "LISTENING"
    echo 解决方案：使用其他端口启动或终止占用进程
    pause
    exit /b 1
)

echo 正在启动服务器，端口: %PORT%
call mvn -pl server compile exec:java -Dexec.mainClass="com.library.server.ServerMain" -Dexec.args="%PORT%" -Dexec.jvmArgs="-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8"
pause





