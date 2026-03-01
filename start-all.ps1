# Hello-Agent 启动脚本 (PowerShell)
# 使用方法: 右键 -> 使用 PowerShell 运行

$ErrorActionPreference = "Continue"

# 设置控制台编码为 UTF-8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "========================================"  -ForegroundColor Cyan
Write-Host "   Hello-Agent 启动脚本" -ForegroundColor Cyan
Write-Host "========================================"  -ForegroundColor Cyan
Write-Host ""

# 检查是否在项目根目录
if (-not (Test-Path "Agent-Studio")) {
    Write-Host "[错误] 请在项目根目录运行此脚本" -ForegroundColor Red
    Read-Host "按回车键退出"
    exit 1
}

# 获取项目根目录
$rootDir = Get-Location
$backendDir = Join-Path $rootDir "Agent-Studio"
$frontendDir = Join-Path $rootDir "frontend"

Write-Host "[信息] 项目根目录: $rootDir" -ForegroundColor Gray
Write-Host "[信息] 后端目录: $backendDir" -ForegroundColor Gray
Write-Host "[信息] 前端目录: $frontendDir" -ForegroundColor Gray
Write-Host ""

# 保存当前目录
Push-Location $rootDir

# 清理函数
function Cleanup {
    Write-Host ""
    Write-Host "[停止] 正在停止所有服务..." -ForegroundColor Yellow

    if ($backendJob) {
        Write-Host "[停止] 后端服务..." -ForegroundColor Gray
        Stop-Job -Name $backendJob.Name -ErrorAction SilentlyContinue
        Remove-Job -Name $backendJob.Name -ErrorAction SilentlyContinue
    }

    if ($frontendJob) {
        Write-Host "[停止] 前端服务..." -ForegroundColor Gray
        Stop-Job -Name $frontendJob.Name -ErrorAction SilentlyContinue
        Remove-Job -Name $frontendJob.Name -ErrorAction SilentlyContinue
    }

    # 杀掉可能残留的进程
    Get-Process | Where-Object {
        $_.ProcessName -eq "java" -and $_.MainWindowTitle -like "*spring-boot*"
    } | Stop-Process -ErrorAction SilentlyContinue

    Pop-Location
    Write-Host "[完成] 所有服务已停止" -ForegroundColor Green
}

# 注册退出时的清理
$null = Register-EngineEvent -SourceIdentifier PowerShell.Exiting -Action {
    Cleanup
} -MaxTriggerCount 1

# Ctrl+C 处理
$null = Register-EngineEvent -SourceIdentifier PowerShell.OnScriptTerminate -Action {
    Cleanup
}

Write-Host "[1/4] 启动后端服务 (Maven)..." -ForegroundColor Cyan
Write-Host ""

# 启动后端
$backendScriptBlock = {
    param($backendDir)
    cd $backendDir
    mvn.cmd spring-boot:run -Dmaven.test.skip=true
}

$backendJob = Start-Job -Name "HelloAgent-Backend" -ScriptBlock $backendScriptBlock -ArgumentList $backendDir

# 后端日志输出
$backendLogTimer = New-Object System.Timers.Timer
$backendLogTimer.Interval = 2000
$backendLogAction = {
    $job = Get-Job -Name "HelloAgent-Backend" -ErrorAction SilentlyContinue
    if ($job) {
        $output = Receive-Job -Job $job -ErrorAction SilentlyContinue
        foreach ($line in $output) {
            if ($line -match "Started|Application|Tomcat|Netty") {
                Write-Host "[后端] $line" -ForegroundColor Green
            } elseif ($line -match "ERROR|Exception|Failed") {
                Write-Host "[后端] $line" -ForegroundColor Red
            } elseif ($line -match "WARN|Warning") {
                Write-Host "[后端] $line" -ForegroundColor Yellow
            } else {
                # 只显示重要日志，避免刷屏
                if ($line -match "Building|Running|Compiled") {
                    Write-Host "[后端] $line" -ForegroundColor Gray
                }
            }
        }
    }
}
Register-ObjectEvent -InputObject $backendLogTimer -EventName Elapsed -Action $backendLogAction | Out-Null
$backendLogTimer.Start()

Write-Host "[后端] 正在启动中..." -ForegroundColor Gray

# 等待后端启动
$backendReady = $false
$maxWait = 60
$waited = 0

while (-not $backendReady -and $waited -lt $maxWait) {
    Start-Sleep -Seconds 2
    $waited += 2

    # 检查端口是否开启
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect("localhost", 8080)
        if ($tcp.Connected) {
            $backendReady = $true
            $tcp.Close()
        }
    } catch {
        Write-Host "." -NoNewline
    }
}

Write-Host ""

if ($backendReady) {
    Write-Host "[后端] ✓ 启动成功！ (http://localhost:8080)" -ForegroundColor Green
} else {
    Write-Host "[后端] ⚠ 启动超时，请检查日志" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[2/4] 启动前端服务 (Vite)..." -ForegroundColor Cyan
Write-Host ""

# 等待一秒
Start-Sleep -Seconds 2

# 启动前端
$frontendScriptBlock = {
    param($frontendDir)
    cd $frontendDir
    npm.cmd run dev
}

$frontendJob = Start-Job -Name "HelloAgent-Frontend" -ScriptBlock $frontendScriptBlock -ArgumentList $frontendDir

# 前端日志输出
$frontendLogTimer = New-Object System.Timers.Timer
$frontendLogTimer.Interval = 2000
$frontendLogAction = {
    $job = Get-Job -Name "HelloAgent-Frontend" -ErrorAction SilentlyContinue
    if ($job) {
        $output = Receive-Job -Job $job -ErrorAction SilentlyContinue
        foreach ($line in $output) {
            if ($line -match "Local:|Network:|ready") {
                Write-Host "[前端] $line" -ForegroundColor Green
            } elseif ($line -match "error|Error|ERROR") {
                Write-Host "[前端] $line" -ForegroundColor Red
            } else {
                Write-Host "[前端] $line" -ForegroundColor Gray
            }
        }
    }
}
Register-ObjectEvent -InputObject $frontendLogTimer -EventName Elapsed -Action $frontendLogAction | Out-Null
$frontendLogTimer.Start()

Write-Host "[前端] 正在启动中..." -ForegroundColor Gray

# 等待前端启动
$frontendReady = $false
$maxWait = 30
$waited = 0

while (-not $frontendReady -and $waited -lt $maxWait) {
    Start-Sleep -Seconds 2
    $waited += 2

    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect("localhost", 5173)
        if ($tcp.Connected) {
            $frontendReady = $true
            $tcp.Close()
        }
    } catch {
        Write-Host "." -NoNewline
    }
}

Write-Host ""

if ($frontendReady) {
    Write-Host "[前端] ✓ 启动成功！ (http://localhost:5173)" -ForegroundColor Green
} else {
    Write-Host "[前端] ⚠ 启动超时，请检查日志" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[3/4] 服务状态检查..." -ForegroundColor Cyan
Write-Host ""

# 检查服务状态
$jobs = Get-Job
Write-Host "运行中的作业:" -ForegroundColor Gray
foreach ($job in $jobs) {
    $status = if ($job.State -eq "Running") { "✓ 运行中" } else { "✗ 已停止" }
    $color = if ($job.State -eq "Running") { "Green" } else { "Red" }
    Write-Host "  - $($job.Name): $status" -ForegroundColor $color
}

Write-Host ""
Write-Host "[4/4] 服务地址" -ForegroundColor Cyan
Write-Host ""
Write-Host "  后端 API: http://localhost:8080" -ForegroundColor White
Write-Host "  前端界面: http://localhost:5173" -ForegroundColor White
Write-Host "  API 文档: http://localhost:8080/doc/index.html" -ForegroundColor White
Write-Host ""

# 打开浏览器
Write-Host "正在打开浏览器..." -ForegroundColor Gray
Start-Sleep -Seconds 2
Start-Process "http://localhost:5173"

Write-Host ""
Write-Host "========================================"  -ForegroundColor Cyan
Write-Host "   服务已全部启动！" -ForegroundColor Green
Write-Host "========================================"  -ForegroundColor Cyan
Write-Host ""
Write-Host "提示:" -ForegroundColor Yellow
Write-Host "  - 按 Ctrl+C 停止所有服务" -ForegroundColor Gray
Write-Host "  - 日志输出将显示在下方" -ForegroundColor Gray
Write-Host "  - 服务将在关闭此窗口时自动停止" -ForegroundColor Gray
Write-Host ""
Write-Host "日志输出 (每 2 秒刷新):" -ForegroundColor Gray
Write-Host "----------------------------------------" -ForegroundColor Gray
Write-Host ""

# 保持脚本运行
try {
    while ($true) {
        Start-Sleep -Seconds 5

        # 检查作业状态
        $backendJobState = (Get-Job -Name "HelloAgent-Backend" -ErrorAction SilentlyContinue).State
        $frontendJobState = (Get-Job -Name "HelloAgent-Frontend" -ErrorAction SilentlyContinue).State

        if ($backendJobState -ne "Running" -and $frontendJobState -ne "Running") {
            Write-Host ""
            Write-Host "[警告] 所有服务都已停止！" -ForegroundColor Red
            break
        }

        # 显示简要状态
        $timestamp = Get-Date -Format "HH:mm:ss"
        $backendStatus = if ($backendJobState -eq "Running") { "✓" } else { "✗" }
        $frontendStatus = if ($frontendJobState -eq "Running") { "✓" } else { "✗" }
        Write-Host "[$timestamp] 后端:$backendStatus 前端:$frontendStatus" -ForegroundColor Gray
    }
} finally {
    Cleanup
}

Write-Host ""
Read-Host "按回车键退出"
