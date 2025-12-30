@echo off
chcp 65001 >nul
echo 正在生成EXCEL示例文件...

cd server
call mvn exec:java -Dexec.mainClass="com.library.server.util.ExcelSampleGenerator" -Dexec.classpathScope=compile -q
cd ..

if exist "示例导入数据_EXCEL.xlsx" (
    echo EXCEL文件生成成功！
) else (
    echo 正在使用备用方法生成EXCEL文件...
    call mvn exec:java -Dexec.mainClass="GenerateExcelSample" -Dexec.classpathScope=compile -q
)

pause



