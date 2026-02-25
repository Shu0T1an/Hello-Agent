# ShellTool 生产版落地计划（Hello-Agent）

## Summary
目标是在当前仓库实现可用于多步任务的生产级 `ShellTool`：支持持久会话、超时控制、输出截断、可重启、会话清理、HITL 审批和最小安全边界，并可在 Agent 配置体系中被选择与使用。  
成功标准：
1. 同一 `sessionId(threadId)` 下多次工具调用可保留 shell 上下文（`cd` 持久）。
2. 每次调用可稳定返回 `stdout/stderr/exitCode/timedOut/truncated`。
3. 超时与异常不会卡死执行链，支持自动重启会话。
4. 工具可在 `tool_definition` 中注册并被 AgentFactory 正常实例化。
5. 默认启用人工审批与目录限制，避免高风险误执行。

## Scope
1. In scope
- `agent-core`：实现 Shell 执行核心与工具接口。
- `Agent-Studio`：注册本地工具、配置项、Agent 默认 hook 策略联动。
- 测试：`agent-core` 单测 + `Agent-Studio` 关键集成测试。
2. Out of scope
- 真正 OS 级沙箱（容器/低权限用户/网络隔离）。
- 前端新增复杂页面，仅复用已有工具管理/Agent 管理能力。

## Architecture
1. 组件划分
- `ShellTool`：Spring AI `@Tool` 暴露给 LLM。
- `ShellSessionManager`：管理持久 shell 进程、命令执行、marker 解析、超时/截断/重启。
- `ShellSessionRegistry`：按 `sessionKey` 管理会话与并发锁。
- `ShellOutputLimiter`：统一截断策略（行数+字节）。
- `ShellToolError`：错误码与标准错误输出格式。
- `ShellToolConfig`：从 `application.yml` 读取配置。
- `ShellToolCleanupScheduler`：定时回收空闲会话（补足无 `afterAgent` 生命周期）。
2. 会话键策略
- `sessionKey = toolContext.toolStateContext.sessionId(threadId) 优先，否则 executionId，否则 "global"`。
- 默认行为：无 `threadId` 时退化为 `executionId` 会话，避免全局串话。
3. Shell 选择策略
- Windows：`powershell.exe -NoProfile -NoLogo -NonInteractive -ExecutionPolicy Bypass`
- Linux/macOS：`/bin/bash -l`
- 启动时检测并记录 shell 类型，失败返回 `SHELL_INIT_FAILED`。

## Public API / Types（新增或变更）
1. 新增工具请求类型
- `ShellCommandRequest`
  - `command: String`（必填）
  - `restart: Boolean`（可选，默认 `false`）
  - `timeoutSeconds: Integer`（可选，受上限约束）
  - `workingDirectory: String`（可选，仅允许白名单目录内）
2. 新增工具返回类型（工具内部结构化后转字符串）
- `ShellCommandResult`
  - `success: boolean`
  - `exitCode: int`
  - `timedOut: boolean`
  - `truncated: boolean`
  - `stdout: String`
  - `stderr: String`
  - `durationMs: long`
  - `sessionRestarted: boolean`
  - `errorCode: String|null`
3. Tool 名称
- `@Tool(name = "execute_shell_command", description = "...")`
4. 配置项（`application.yml`）
- `agent.shelltool.enabled=true`
- `agent.shelltool.default-timeout-seconds=30`
- `agent.shelltool.max-timeout-seconds=120`
- `agent.shelltool.max-output-lines=400`
- `agent.shelltool.max-output-bytes=131072`
- `agent.shelltool.idle-ttl-seconds=900`
- `agent.shelltool.cleanup-interval-seconds=60`
- `agent.shelltool.auto-restart-on-timeout=true`
- `agent.shelltool.allowed-working-directories=[D:/JavaProject/Hello-Agent]`
- `agent.shelltool.blocked-command-patterns=[...]`（默认阻断高风险模式）

## Detailed Implementation Plan
1. `agent-core` 实现 Shell 执行核心
- 新增 `cn.ts.agent.tool.shell` 包。
- `ShellSessionManager` 持有 `Process`、stdin writer、stdout/stderr reader 线程、安全队列、最后活跃时间、互斥锁。
- 执行流程：
  1. 校验请求与目录权限。
  2. `restart=true` 时先关闭旧会话再重建。
  3. 向 stdin 写入用户命令。
  4. 写入 marker 命令并带 exit code。
  5. 轮询读取直至 marker 或超时。
  6. 应用截断策略，构造结果。
  7. 超时时按配置自动重启并返回 `timedOut=true`。
2. marker 协议
- PowerShell marker：`Write-Output "__HA_MARKER__ $LASTEXITCODE"`
- Bash marker：`printf '__HA_MARKER__ %s\n' $?`
- 解析规则：第一条匹配 marker 的行判定命令边界与 `exitCode`。
3. 并发模型
- 同一 `sessionKey` 串行执行（`ReentrantLock`）。
- 不同 `sessionKey` 并行执行。
- 防止同会话并发写 stdin 导致输出交叉。
4. 生命周期与清理
- 无 `beforeAgent/afterAgent` 场景采用两级清理：
  - 调用级：`restart` 或异常时即时重建/销毁。
  - 系统级：`ShellToolCleanupScheduler` 定时清理空闲超 TTL 会话。
- JVM shutdown hook：进程统一销毁。
5. 安全与审批
- 在 `AgentConfig` 默认 hook 中为 `execute_shell_command` 增加 `HumanInTheLoopHook.approvalOn(...)`。
- 命令过滤：
  - Windows 阻断默认模式：`Remove-Item -Recurse`, `Format-Volume`, `diskpart`, `reg delete` 等。
  - Unix 阻断默认模式：`rm -rf /`, `mkfs`, `dd if=`, `shutdown`, `reboot` 等。
- 目录限制：`workingDirectory` 必须归属白名单根路径。
6. 与现有工具体系对齐
- 新增工具类放入可扫描包。
- 修复 `LocalToolScanner` 的扫描包常量为 `cn.ts.web.tool`（或同时支持 `cn.ts.web.tool` + `cn.ts.web.tools`），避免本地工具漏注册。
- `ToolDefinitionService.instantiateTools` 保持按类去重，不需改签名。
7. Agent 装配
- `AgentConfig` 内置 agent 的 `tools` 列表增加 `ShellTool` Bean。
- 数据库动态 Agent 通过 `tool_definition` 绑定后可用，无需改 `AgentFactory` 主流程。
8. 结果格式标准化
- 工具最终返回文本模板：
  - 首段：`status/exitCode/duration/timedOut/truncated`
  - 中段：`stdout`
  - 末段：`stderr`（仅非空显示）
- 与 `ToolNode` 现有 `ToolResponseMessage` 完全兼容。

## File-Level Change List
1. 新增
- `agent-core/src/main/java/cn/ts/agent/tool/shell/ShellTool.java`
- `agent-core/src/main/java/cn/ts/agent/tool/shell/ShellSessionManager.java`
- `agent-core/src/main/java/cn/ts/agent/tool/shell/ShellSessionRegistry.java`
- `agent-core/src/main/java/cn/ts/agent/tool/shell/ShellToolConfig.java`
- `agent-core/src/main/java/cn/ts/agent/tool/shell/ShellOutputLimiter.java`
- `agent-core/src/main/java/cn/ts/agent/tool/shell/ShellToolException.java`
- `Agent-Studio/src/main/java/cn/ts/web/tool/local/ShellToolCleanupScheduler.java`
2. 修改
- `Agent-Studio/src/main/java/cn/ts/web/shared/config/AgentConfig.java`
- `Agent-Studio/src/main/java/cn/ts/web/shared/component/LocalToolScanner.java`
- `Agent-Studio/src/main/resources/application.yml`
- `Agent-Studio/src/main/resources/application-dev.yml`
- `Agent-Studio/src/main/resources/db/data.sql`（可选：预置 tool_definition 示例）
3. 可选修改
- `ToolManagementController` 的 `scan-local` TODO 可顺手补齐，便于手动重扫验证。

## Test Plan
1. `agent-core` 单测
- `ShellSessionManagerTest`
  - 同会话 `cd` 持久验证。
  - marker 能正确解析 exit code。
  - 超时触发 `timedOut=true` 与自动重启。
  - 超长输出截断标记为 `truncated=true`。
  - 并发下同会话串行、跨会话并行。
- `ShellToolTest`
  - 空命令/非法目录/阻断命令返回对应错误码。
  - `restart=true` 行为正确。
2. `Agent-Studio` 集成测试
- `LocalToolScanner` 可扫描并 upsert `execute_shell_command`。
- `ToolDefinitionService.instantiateTools` 能实例化 `ShellTool`。
- `AgentFactory` 绑定该工具后可执行。
- HITL 对 `execute_shell_command` 触发审批中断。
3. 手工验收场景
- 场景A：`pwd` -> `cd subdir` -> `pwd`，目录应变化且持久。
- 场景B：执行长命令超时后再执行 `echo ok`，应可恢复。
- 场景C：输出爆量命令，返回被截断且系统不阻塞。
- 场景D：危险命令被拒绝并记录审计日志。

## Rollout & Monitoring
1. 灰度
- 配置开关 `agent.shelltool.enabled=false` 默认关闭，先在 dev 打开。
- 仅对测试 Agent 先挂载，稳定后推广到动态 Agent。
2. 监控指标
- `shelltool_calls_total`
- `shelltool_failures_total`
- `shelltool_timeouts_total`
- `shelltool_restarts_total`
- `shelltool_active_sessions`
- `shelltool_truncations_total`
3. 日志与审计
- 记录 `sessionKey/executionId/toolCallId/duration/exitCode`。
- 命令内容默认脱敏后日志化（可配置是否记录明文）。

## Risks & Mitigations
1. 风险：命令执行安全边界不足。
- 缓解：默认 HITL + 白名单目录 + 命令阻断模式 + 配置开关。
2. 风险：无 `afterAgent` 造成进程泄漏。
- 缓解：TTL 清理 + shutdown hook + 超时自动重启。
3. 风险：平台差异导致 marker 解析失败。
- 缓解：按 shell 类型分支 marker，并加兼容测试。

## Assumptions and Defaults
1. 以“生产版（持久会话）”为唯一目标，不再单独交付最小 PoC。
2. 默认以 `threadId(sessionId)` 作为会话隔离键，缺失时退化到 `executionId`。
3. 默认开启 `execute_shell_command` 的人工审批。
4. 默认只允许工作目录在 `D:/JavaProject/Hello-Agent` 内。
5. 不引入新数据库表，工具定义仍走现有 `tool_definition`。
