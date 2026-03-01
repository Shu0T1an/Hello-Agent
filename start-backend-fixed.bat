@echo off
chcp 65001 >nul
echo ========================================
echo    Hello-Agent 后端启动 (编码修复版)
echo ========================================
echo.

REM 设置 UTF-8 编码
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8
set MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8

echo [信息] JVM 编码参数: %JAVA_TOOL_OPTIONS%
echo [信息] Maven 编码参数: %MAVEN_OPTS%
echo.

cd /d %~dp0Agent-Studio

echo [启动] Maven Spring Boot...
echo.
echo 提示:
echo - 服务地址: http://localhost:8080
echo - 健康检查: http://localhost:8080/actuator/health
echo - 按 Ctrl+C 停止服务
echo.

mvn spring-boot:run -Dmaven.test.skip=true -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8"

pause
