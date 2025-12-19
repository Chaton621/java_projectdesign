@echo off
chcp 65001 >nul
cd /d %~dp0
set MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8
call mvn -pl client compile javafx:run
pause




