@echo off
chcp 65001 >nul
echo ========================================
echo    Hello-Agent 启动脚本
echo ========================================
echo.

REM 检查是否在项目根目录
if not exist "Agent-Studio" (
    echo [错误] 请在项目根目录运行此脚本
    pause
    exit /b 1
)

echo [1/4] 启动后端服务...
echo.
start "Hello-Agent Backend" cmd /k "chcp 65001 >nul && cd /d %~dp0Agent-Studio && set MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 && mvn spring-boot:run -Dmaven.test.skip=true -Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8"

echo [后端] 已在新窗口启动，请等待服务启动...
echo.

timeout /t 3 /nobreak >nul

echo [2/4] 启动前端服务...
echo.
start "Hello-Agent Frontend" cmd /k "cd /d %~dp0frontend && npm run dev"

echo [前端] 已在新窗口启动...
echo.

echo [3/4] 等待服务启动...
echo.
echo 后端地址: http://localhost:8080
echo 前端地址: http://localhost:5173
echo.

timeout /t 5 /nobreak >nul

echo [4/4] 打开浏览器...
echo.
timeout /t 2 /nobreak >nul
start http://localhost:5173

echo.
echo ========================================
echo    服务已全部启动！
echo ========================================
echo.
echo 提示：
echo - 后端日志窗口标题: "Hello-Agent Backend"
echo - 前端日志窗口标题: "Hello-Agent Frontend"
echo - 关闭窗口即可停止对应服务
echo.
pause
