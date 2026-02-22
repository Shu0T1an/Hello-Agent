# 目录改造实施计划（持久化）

## 1. 背景
- 当前后端包结构存在按技术分层与按业务分层混用的问题。
- 随着模块增长，`controller/service/entity/mapper` 等横向目录在同一层级下维护成本升高。
- 目标是在不改变业务行为的前提下，完成按领域归档与执行职责隔离。

## 2. 改造目标
- `agent-studio`：从“纯技术分层”调整为“领域优先 + 技术分层”。
- `agent-core`：统一包名规范（`Prompt/Tool` -> `prompt/tool`）。
- `graph-core`：将节点执行编排相关类收敛到 `execution`，执行记录服务收敛到 `record`。
- 全过程保持可编译，逐批提交，降低回滚成本。

## 3. 分阶段实施清单

### Phase A: agent-studio 领域化改造
- `session/*` 归档：`controller/service/dto/entity/mapper` + `strategy`
- `agent/*` 归档：`controller/service/dto/entity/mapper/factory`
- `rag/*` 归档：`controller/service/dto/entity/mapper`
- `tool/*` 归档：`controller/service/entity/mapper/local`
- `infra/*` 归档：`mcp/checkpoint/tempfile/testsupport`
- `shared/*` 归档：`config/constant/response/exception/service/component`
- 配置同步：
  - `MyBatisConfig.@MapperScan` 增加多包扫描
  - `setTypeAliasesPackage` 同步实体包

### Phase B: agent-core 包名规范化
- 将 `cn.ts.agent.Prompt` 引用统一到 `cn.ts.agent.prompt`
- 将 `cn.ts.agent.Tool` 引用统一到 `cn.ts.agent.tool`
- 以“引用修复 + 编译通过”为验收标准

### Phase C: graph-core 执行职责拆分
- 迁移至 `cn.ts.graph.execution`：
  - `NodeExecutor`
  - `NodeActionInvoker`
  - `NodeInterruptionService`
  - `NodeResultAssembler`
- 迁移至 `cn.ts.graph.record`：
  - `ExecutionRecordService`
- 同步修复：
  - `GraphRunner` 对 `NodeExecutor` 的 import
  - `execution/*` 与 `record/*` 间跨包 import
  - `ExecutionRecordService` 可见性调整（供 `execution` 调用）

## 4. 已完成提交记录
1. `078fe61` `refactor(agent-studio): domainize session package layout`
2. `d660cfd` `refactor(agent-studio): domainize agent package layout`
3. `37df07c` `refactor(agent-studio): domainize rag tool infra and shared packages`
4. `31f2bb0` `refactor(agent-core): normalize prompt and tool package naming`
5. `4d1e891` `refactor(graph-core): isolate node execution orchestration package`

## 5. 验证命令与结果
- `mvn -pl agent-studio -DskipTests compile`：通过
- `mvn -pl agent-core -DskipTests test-compile`：通过
- `mvn -pl graph-core -DskipTests compile`：通过
- `mvn -pl graph-core,agent-core,agent-studio -DskipTests compile`：通过

## 6. 风险与注意事项
- IDE 工程文件（如 `.idea/workspace.xml`）不纳入提交。
- Windows 下目录大小写显示可能与 Git rename 记录不一致，以 Git 历史为准。
- 注释乱码问题属于文件编码与终端显示链路问题，后续新增/修改建议统一 UTF-8（无 BOM）。

## 7. 后续建议
1. 补一轮跨模块 smoke test（重点：SSE 流式执行、MCP、会话恢复）。
2. 对迁移包增加 architecture test，防止后续回流到旧包结构。
3. 在 `docs/` 增补“包结构约定”文档，作为后续开发规范基线。
