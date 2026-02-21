# SubAgent 实时进度与 Timeline 改造说明

## 1. 目标

本次改造目标：

1. 在开启 subagent 的场景下，前端可以实时看到各 subagent 的执行进度。
2. 复用现有 SSE 通道，不新增 WebSocket。
3. 时间线从“事件堆叠”提升为“可观测执行面板”。

---

## 2. 当前已完成实现

## 2.1 后端：SubAgent 进度事件模型与上报链路

已新增以下能力：

1. 新增 subagent 进度事件模型与上报接口（agent-core）
   - `agent-core/src/main/java/cn/ts/agent/extension/progress/SubAgentProgressEvent.java`
   - `agent-core/src/main/java/cn/ts/agent/extension/progress/SubAgentProgressReporter.java`

2. `TaskTool` 改造为可发进度事件
   - 文件：`agent-core/src/main/java/cn/ts/agent/extension/tools/TaskTool.java`
   - 新事件：
     - `SUBAGENT_STARTED`
     - `SUBAGENT_PROGRESS`
     - `SUBAGENT_COMPLETED`
     - `SUBAGENT_FAILED`
   - 上报 metadata（核心字段）：
     - `subagentTaskId`
     - `subagentType`
     - `parentToolCallId`
     - `parentExecutionId`
     - `phase`
     - `progress`
     - `durationMs`
     - `summary`
     - `errorCode`
     - `errorMessage`

3. SubAgent 有限并发控制（默认 3）
   - 通过 `Semaphore` 控制并发。
   - 资源不足时上报 `phase=queued`。

4. `SubAgentInterceptor` 支持注入进度上报器
   - 文件：`agent-core/src/main/java/cn/ts/agent/extension/interceptor/SubAgentInterceptor.java`

## 2.2 后端：SSE 合流与总线

1. 新增内存进度总线（Agent-Studio）
   - 文件：`Agent-Studio/src/main/java/cn/ts/web/service/SubAgentProgressBus.java`
   - 按 `executionId` 管理事件流。

2. 主执行流和 subagent 进度流合并
   - 文件：`Agent-Studio/src/main/java/cn/ts/web/service/AgentExecutionService.java`
   - `Flux.merge(graphStream, subAgentStream)` 输出统一 SSE。

3. 节点类型补充
   - 文件：`Agent-Studio/src/main/java/cn/ts/web/constant/ApiConstants.java`
   - 新增 `NodeTypes.SUBAGENT = "subagent"`。

4. 深度搜索配置补充
   - 文件：`Agent-Studio/src/main/java/cn/ts/web/agent/deepsearch/DeepSearchProperties.java`
   - 文件：`Agent-Studio/src/main/resources/application.yml`
   - 新增 `maxParallelSubagents`（默认 3）。

5. 注入链路打通
   - 文件：`Agent-Studio/src/main/java/cn/ts/web/agent/deepsearch/DeepSearchAgentBuilder.java`
   - 文件：`Agent-Studio/src/main/java/cn/ts/web/factory/AgentFactory.java`

## 2.3 前端：Timeline 展示与聚合优化

1. 类型系统扩展
   - 文件：`frontend/src/types/agent.ts`
   - 新增 `SUBAGENT_*` 事件类型、`subagent` 节点类型、`SubAgentMetadata`。

2. SSE 数据透传 metadata
   - 文件：`frontend/src/stores/chat.ts`
   - 让 timeline 能拿到后端下发的 subagent 业务字段。

3. 时间线事件识别与样式
   - 文件：`frontend/src/utils/agentEvents.ts`
   - 支持 `isSubAgentEvent` 和 subagent 主题配置。

4. Timeline UI 新增 subagent 卡片
   - 文件：`frontend/src/components/agent/AgentTimeline.vue`
   - 展示字段：
     - 类型（subagentType）
     - 阶段（phase）
     - 进度（progress）
     - 耗时（durationMs）
     - 摘要/错误信息（summary/errorMessage）

5. DeepSearch 自动打开时间线
   - 文件：`frontend/src/components/chat/ChatContainer.vue`

6. 聚合与总览（已做）
   - 在 `AgentTimeline.vue` 中按 `subagentTaskId` 聚合事件，避免刷屏。
   - 新增顶部 overview：`total/running/queued/done/failed`。

## 2.4 测试代码适配

已同步修改受构造器变更影响的测试：

1. `Agent-Studio/src/test/java/cn/ts/web/agent/deepsearch/DeepSearchAgentBuilderTest.java`
2. `Agent-Studio/src/test/java/cn/ts/web/factory/AgentFactorySubAgentIntegrationTest.java`
3. `Agent-Studio/src/test/java/cn/ts/web/service/SessionManagementTest.java`

---

## 3. 已执行验证

1. 后端编译通过
   - `mvn -pl agent-core,Agent-Studio -DskipTests compile`

2. 前端类型检查通过
   - `cd frontend && npx vue-tsc -b`

3. 环境性阻碍（非本次改动逻辑错误）
   - `frontend` 完整构建受本地 Node 版本约束（Vite 需要更高版本）及 `esbuild spawn EPERM` 影响。
   - 仓库现有若干测试文件存在与 Spring AI 版本相关的构造器不匹配问题，导致全量 test compile 失败（与本次改造无直接关联）。

---

## 4. 还需要改进的部分（建议优先级）

## P0（建议优先做）

1. Timeline 增加筛选器
   - 维度：`全部 / 主流程 / SubAgent / 失败`
   - 目的：降低噪音，提升定位效率。

2. 卡片默认展开策略
   - 失败默认展开，完成默认折叠，运行中保持展开。

3. 子任务排序策略
   - 运行中置顶；其余按最近更新时间排序。

## P1（第二阶段）

1. 子任务树形视图
   - 显示 parent-child（主工具调用 -> subagent 任务）。

2. 关键指标增强
   - 每个 subagent 卡片显示：
     - 工具调用次数
     - token 使用量（若可获取）
     - 重试次数

3. 进度语义标准化
   - 统一 phase 枚举：`queued/planning/running/synthesizing/done/failed`。

## P2（可选增强）

1. token 级流式显示开关
   - 默认关闭，仅在调试模式开启，避免 UI 过载。

2. 长任务容错
   - 针对 SSE 重连做去重与补偿逻辑（按 `subagentTaskId + event seq`）。

3. 持久化执行快照
   - 将 subagent 关键进度写入会话/检查点，支持刷新恢复。

---

## 5. 风险与注意事项

1. 当前事件总线是内存实现，多实例部署时需升级为共享总线（Redis Stream/Kafka 等）。
2. `summary` 目前在后端做了长度截断，应继续约束字段上限，避免大包导致 SSE 背压。
3. 如果后续开启 token 级流式，需提前设计前端节流策略（批量合并渲染）。

---

## 6. 结论

当前版本已经实现了：

1. subagent 实时进度可见（SSE 实时传输）。
2. 时间线可用于实际观测（不再只是原始事件堆叠）。
3. DeepSearch 场景下用户能直接看到“谁在跑、跑到哪、是否失败、耗时多久”。

下一步建议按 P0 继续完善交互，以进一步提升可用性和问题定位效率。
