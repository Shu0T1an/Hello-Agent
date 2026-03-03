# CoPaw 与 Hello-Agent 功能对比（代码级）

- 对比日期：2026-03-02
- CoPaw 代码基线：`D:\AI\Github\CoPaw`（`main`，commit `a80a2bdfcb0bfbea25fd965edea69f47932d280a`）
- Hello-Agent 代码基线：`D:\JavaProject\Hello-Agent`（当前工作区）
- 对比方法：基于源码结构、后端接口、关键服务实现和前端 store/API 模块，不仅依据 README 文案。

## 1. 总体定位差异

- CoPaw：偏“个人助理工作台”，强调**多聊天渠道接入 + 工作目录驱动 + skills/cron 可组合自动化**。
- Hello-Agent：偏“可编排 Agent 平台”，强调**图执行引擎 + 中断/检查点恢复 + 会话化 SSE + RAG/MCP 管理后台**。

## 2. 功能矩阵（结论速览）

| 维度 | CoPaw | Hello-Agent | 结论 |
|---|---|---|---|
| 技术栈 | Python + FastAPI + AgentScope Runtime | Java 21 + Spring Boot + Vue3 | 技术路线不同，定位互补 |
| 多渠道接入（钉钉/飞书/QQ/Discord/iMessage） | 原生支持，并支持自定义 channel 插件 | 未见对应渠道实现，主要是 Web UI + API | CoPaw 明显更强 |
| Web Console | 内置 Console（静态资源 + API） | 前后端分离（agent-studio + frontend） | 都支持 |
| Agent 流式执行 | 支持（runner/agent app） | 支持（SSE `/api/stream/...`） | 都支持 |
| 人机中断与恢复（HITL） | 未见完整 checkpoint+resume 机制 | 有中断检测、检查点创建、恢复接口与前端交互 | Hello-Agent 更强 |
| 检查点与状态快照 | 未见独立 checkpoint 子系统 | graph-core + `/api/checkpoints` 完整支持 | Hello-Agent 更强 |
| Skills 生命周期管理 | 列表/创建/启停/删除/Hub 安装/文件读取 | 目前偏“注册与检索”（list/detail/reference/reindex） | CoPaw 更强 |
| Skills Hub 远程安装 | 支持（clawhub/skills.sh/GitHub/skillsmp） | 未见同等能力 | CoPaw 更强 |
| MCP 管理 | 有 MCP manager + 热更新 watcher + 路由 | 有连接管理、健康检查、重连、统计、工具同步 | 两者都强，Hello-Agent 管理面更细 |
| 定时任务/心跳调度 | CronManager + APScheduler + heartbeat | 仅 SSE 心跳端点（非业务 cron） | CoPaw 更强 |
| RAG 知识库管理 | 未见独立 RAG 模块路由 | 文档上传、检索、流式问答、知识库 CRUD | Hello-Agent 更强 |
| 本地模型下载/管理 | 提供 local-models 下载任务与状态管理 | 模型配置管理为主，未见下载任务链路 | CoPaw 更强 |
| 文件系统工具 | 内置 file/tool/browser/shell 等 | 有本地 FS 工具（read/write/edit/glob/grep）+ 路径策略 | 都较强 |
| 记忆系统 | MemoryManager（语义检索、压缩摘要、memory_search/get） | MemoryProvider + Prompt 注入（markdown 文件） | CoPaw 更深，Hello-Agent 更轻量 |
| 会话管理 | JSON 会话（含 Windows 文件名安全处理）+ chats API | 会话 CRUD + 消息 + 审计 + 标题 + 汇总 | Hello-Agent 平台能力更强 |

## 3. 关键差异解读

### 3.1 交互入口：CoPaw“多渠道优先”，Hello-Agent“平台 API 优先”

- CoPaw 的 `ChannelManager`、`channels/registry.py`、`channels_cmd.py` 构成了“多渠道统一入口 + 可插拔 channel”体系。
- Hello-Agent 当前主要暴露 Web 后台与前端 UI，未形成类似的外部 IM 渠道适配层。

适用场景判断：
- 如果目标是“接入企业 IM、个人多终端消息触达”，CoPaw 架构更近。
- 如果目标是“作为可二次开发的 Agent 平台与业务系统集成”，Hello-Agent 更近。

### 3.2 执行控制：Hello-Agent 在可恢复执行上更成熟

- Hello-Agent 在 graph-core 有中断检测、checkpoint 存储、restore API，以及前端 `chat.ts` 的 resume 流程。
- CoPaw 主要是 runner + session 持久化与流式执行，未看到同等粒度的“中断审批 -> 恢复执行”闭环。

这意味着 Hello-Agent 在“高风险工具调用审批、人类确认后继续执行”方面更有工程基础。

### 3.3 Skills 生态：CoPaw 是完整生命周期，Hello-Agent 当前偏索引浏览

- CoPaw skills 路由直接支持 create/enable/disable/delete/hub install；`skills_hub.py` 还实现了多源拉取与解析。
- Hello-Agent 当前 skill 接口以 list/detail/reference/reindex 为主，更像“技能目录检索服务”。

若你计划把 skills 做成“可安装、可启停、可分发”的生态，CoPaw 的这部分可以直接作为参考蓝本。

### 3.4 业务能力重心：Hello-Agent 有完整 RAG 管理面

- Hello-Agent 提供知识库 CRUD、文档上传、相似检索、流式问答等后端能力。
- CoPaw 的强项在助手工作流与 skills 组合，RAG 在当前代码结构里不是独立的一等模块。

### 3.5 运维与自动化：CoPaw 的 cron/heartbeat 更偏“个人助理自动化”

- CoPaw 有作业仓库、调度器、手动触发、暂停恢复、状态查询。
- Hello-Agent 目前无等价的业务 cron 调度模块（有 SSE heartbeat，但语义不同）。

## 4. 可直接借鉴到 Hello-Agent 的 CoPaw 设计点

1. Skills 生命周期管理闭环
- 增加 enable/disable/create/delete/import（含 Hub）接口。
- 把 `skills` 从“只读索引”升级为“运行时可控能力包”。

2. 多渠道插件化框架
- 抽象统一 `BaseChannel` 和 registry，先做 1-2 个渠道 PoC（如钉钉/飞书）。
- 与现有 `SessionService`、`StreamController` 建立 channel/session 映射。

3. 任务调度层
- 引入 cron job 模型（任务定义、触发器、并发限制、重试、状态）。
- 结合现有 agent 执行接口，支持“定时执行 Agent 并推送结果”。

4. 本地模型下载任务化
- 参考 CoPaw 的 task store 设计，补齐“下载、取消、状态追踪、完成通知”链路。

## 5. Hello-Agent 当前相对 CoPaw 的优势能力

1. 图执行可观测与可恢复
- 中断点、检查点、恢复执行、时间线映射等能力更完整。

2. RAG 管理面完整
- 知识库生命周期管理与流式查询接口已经成体系。

3. 平台化后端边界更清晰
- `controller -> service -> mapper/entity` 分层，便于企业化治理与扩展。

## 6. 风险与注意点

- CoPaw 仓库使用者/权限与当前沙箱用户不同，`git` 命令在该目录会触发 safe.directory 限制；本次通过文件内容读取完成对比。
- 两个项目定位不完全重叠，不建议简单做“功能一比一搬运”；建议按“目标场景”决定融合路线。

## 7. 证据文件（节选）

### CoPaw
- `src/copaw/cli/main.py`
- `src/copaw/cli/channels_cmd.py`
- `src/copaw/cli/skills_cmd.py`
- `src/copaw/app/_app.py`
- `src/copaw/app/routers/__init__.py`
- `src/copaw/app/routers/skills.py`
- `src/copaw/app/routers/local_models.py`
- `src/copaw/app/routers/providers.py`
- `src/copaw/app/routers/workspace.py`
- `src/copaw/app/channels/manager.py`
- `src/copaw/app/channels/registry.py`
- `src/copaw/app/crons/manager.py`
- `src/copaw/app/mcp/manager.py`
- `src/copaw/agents/skills_manager.py`
- `src/copaw/agents/skills_hub.py`
- `src/copaw/agents/memory/memory_manager.py`
- `src/copaw/app/runner/runner.py`

### Hello-Agent
- `agent-studio/src/main/java/cn/ts/web/session/controller/StreamController.java`
- `agent-studio/src/main/java/cn/ts/web/infra/checkpoint/controller/CheckpointController.java`
- `graph-core/src/main/java/cn/ts/graph/checkpoint/CheckpointManager.java`
- `graph-core/src/main/java/cn/ts/graph/execution/NodeInterruptionService.java`
- `agent-core/src/main/java/cn/ts/agent/extension/progress/SubAgentStreamMapper.java`
- `agent-studio/src/main/java/cn/ts/web/skills/controller/SkillController.java`
- `agent-studio/src/main/java/cn/ts/web/skills/service/SkillRegistryService.java`
- `agent-studio/src/main/java/cn/ts/web/infra/mcp/controller/McpController.java`
- `agent-studio/src/main/java/cn/ts/web/rag/controller/RagController.java`
- `agent-studio/src/main/java/cn/ts/web/tool/controller/ToolManagementController.java`
- `agent-studio/src/main/java/cn/ts/web/tool/local/fs/FileOpsService.java`
- `agent-studio/src/main/java/cn/ts/web/tool/local/fs/SearchService.java`
- `agent-studio/src/main/java/cn/ts/web/memory/interceptor/MemoryPromptInterceptor.java`
- `frontend/src/stores/chat.ts`
- `frontend/src/api/*.ts`
