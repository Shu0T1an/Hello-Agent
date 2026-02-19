@echo off
chcp 65001 >nul
echo ========================================
echo    Hello-Agent 停止脚本
echo ========================================
echo.

echo [1/3] 停止后端服务 (Java)...
taskkill /F /FI "WINDOWTITLE eq Hello-Agent Backend*" /T 2>nul
taskkill /F /IM java.exe /FI "WINDOWTITLE eq *spring-boot*" 2>nul
echo 后端服务已停止
echo.

echo [2/3] 停止前端服务 (Node)...
taskkill /F /FI "WINDOWTITLE eq Hello-Agent Frontend*" /T 2>nul
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173"') do (
    taskkill /F /PID %%a 2>nul
)
echo 前端服务已停止
echo.

echo [3/3] 清理端口占用...
echo 检查 8080 端口...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080"') do (
    echo 停止 PID %%a
    taskkill /F /PID %%a 2>nul
)
echo 检查 5173 端口...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173"') do (
    echo 停止 PID %%a
    taskkill /F /PID %%a 2>nul
)
echo.

echo ========================================
echo    所有服务已停止
echo ========================================
echo.
timeout /t 2 /nobreak >nul
