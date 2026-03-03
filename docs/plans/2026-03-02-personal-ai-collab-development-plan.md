# 个人项目 AI 协同开发主计划（Hello-Agent）

- 版本：V1
- 日期：2026-03-02
- 项目：`D:\JavaProject\Hello-Agent`
- 目标：为“你 + AI”提供一份长期可维护、可执行、可追踪的开发导航文档。

---

## 1. 这份文档怎么用

每次让 AI 开发前，先让它读取本文件，并按以下顺序执行：

1. 读取“项目上下文包”
2. 确认“已完成功能”与“当前目标功能”
3. 先输出最小可行方案（MVP）
4. 再执行开发 + 测试 +回归验证
5. 最后更新本文件中的“进展记录”

建议固定提示词：

```text
请先阅读 docs/plans/2026-03-02-personal-ai-collab-development-plan.md，
按“当前迭代目标”执行开发，不要偏离范围。
开发完成后：
1) 输出改动文件清单
2) 输出测试结果
3) 回填文档中的进展记录与风险状态。
```

---

## 2. 项目上下文包（给 AI 的最小必要信息）

## 2.1 仓库结构

- `graph-core/`：图执行引擎、状态与检查点能力
- `agent-core/`：Agent 运行逻辑、工具/MCP 相关核心能力
- `agent-studio/`：Spring Boot 后端 API
- `frontend/`：Vue3 + TS 前端
- `docs/`：设计文档与计划
- `skills/`：技能目录（本地）

## 2.2 基础规范

- Java 21，Spring Boot，分层结构：`controller -> service -> mapper/entity`
- 前端 Vue3 + Pinia + TypeScript
- 提交信息使用中文

## 2.3 参考资料（固定）

- CoPaw 源码路径：`D:\AI\Github\CoPaw`
- 对比分析文档：[CoPaw 与 Hello-Agent 功能对比](../../copaw-vs-hello-agent-2026-03-02.md)
- 需求评审与排期：[对齐 CoPaw 的需求评审与排期](./2026-03-02-copaw-alignment-requirements-review-and-schedule.md)
- CoPaw 源码参考索引：[Core File Map](../../skills/copaw-source-analysis/references/core-file-map.md)
- CoPaw 本地分析 Skill：[copaw-source-analysis/SKILL.md](../../skills/copaw-source-analysis/SKILL.md)
- 当前文档（AI 协同主计划）：[personal-ai-collab-development-plan](./2026-03-02-personal-ai-collab-development-plan.md)

---

## 3. 当前已完成能力（基线）

以下能力可视为“已有资产”，新需求应优先复用：

1. 图执行与流式输出
- SSE 执行与心跳（`StreamController`）
- 前端流式消息处理与中断恢复（`frontend/src/stores/chat.ts`）

2. 中断与恢复（HITL）
- 中断检测、checkpoint 创建与恢复
- 检查点查询/恢复 API（`CheckpointController`）

3. RAG 管理能力
- 文档上传、检索、流式查询
- 知识库 CRUD（`RagController`）

4. MCP 管理能力
- 连接增删改查、健康检查、重连、统计（`McpController`）

5. 技能索引能力（只读偏管理）
- 列表、详情、引用读取、reindex（`SkillController` + `SkillRegistryService`）

---

## 4. 目标能力地图（做成“类似 CoPaw”）

## 4.1 P0（必须先做）

1. 渠道插件框架（channel 抽象 + registry + manager）
2. 首批渠道接入（钉钉、飞书二选一先打通）
3. cron 任务中心（CRUD + 启停 + 立即执行 + 状态）
4. skills 生命周期（create/enable/disable/delete）

## 4.2 P1（形成完整体验）

1. skills Hub 导入（先支持 GitHub URL）
2. 本地模型下载任务中心（下载/取消/状态）
3. workspace 导入导出（zip）

## 4.3 P2（生产化增强）

1. CLI 运维入口（init/app/channels/skills/cron）
2. 安全加固（密钥加密、导入校验、审计）
3. 压测和稳定性修复

---

## 5. 个人研发节奏（建议）

## 5.1 周节奏（单人可执行）

- 周一：需求拆分 + AI 生成实施计划
- 周二到周四：实现 + 单测 + 联调
- 周五：回归测试 + 文档更新 + 下周规划

## 5.2 每日节奏（2-4 小时有效开发）

1. 20 分钟：明确当日唯一目标
2. 90 分钟：AI 驱动实现（严格小步提交）
3. 30 分钟：测试与回归
4. 20 分钟：更新进展记录

---

## 6. AI 执行协议（核心）

每个任务都要求 AI 遵守以下协议：

1. 先说“将修改哪些文件，为什么修改”
2. 再动手改代码
3. 改完必须跑测试（至少相关模块测试）
4. 输出结果时必须包含：
- 改动文件列表
- 风险点
- 未完成项
- 下一步建议

### 6.1 单任务提示词模板

```text
目标：<一句话目标>
范围：只允许改动 <目录/文件>
参考：<文档路径或源码路径>
约束：
1) 先给最小改动方案
2) 必须补测试
3) 不要重构无关代码
完成标准：
1) 功能可用
2) 测试通过
3) 更新 docs/plans 中的进展记录
```

### 6.2 防跑偏提示词模板

```text
你现在只做这个子任务：<子任务名>。
禁止做：<列出不做的项>。
如果发现额外问题，先记录到“风险与待办”，不要扩展实现范围。
```

---

## 7. 任务卡模板（建议每个功能一张）

```markdown
### 任务卡：<功能名>
- 目标：
- 输入：
- 输出：
- 涉及文件：
- 验收标准：
- 测试清单：
- 风险：
- 结果：
```

---

## 8. 当前迭代目标（可直接执行）

### Sprint-A（建议先做，2 周）

1. 渠道框架最小闭环
- 输出：channel 抽象、registry、manager
- 验收：可通过模拟渠道触发一次 agent 执行

2. skills 生命周期 API（先后端）
- 输出：enable/disable/create/delete/list/detail
- 验收：前端可启停技能并立即生效

3. cron 基础任务中心（后端先行）
- 输出：任务 CRUD + run now + state
- 验收：任务可触发并记录状态

### Sprint-B（第 3-4 周）

1. 接入首个真实渠道（建议钉钉）
2. 增加 skills Hub GitHub 导入
3. 补全前端管理页与基础回归

---

## 9. 进展记录（每周更新）

## Week 1

- 计划：
- 实际完成：
- 未完成：
- 风险变化：

## Week 2

- 计划：
- 实际完成：
- 未完成：
- 风险变化：

---

## 10. 风险台账（持续维护）

1. 渠道协议变更风险
- 状态：Open
- 影响：高
- 缓解：适配层隔离 + 集成测试

2. 单人开发并行度不足
- 状态：Open
- 影响：中
- 缓解：严格优先级，限制同时进行任务数 <= 2

3. AI 产出偏离需求
- 状态：Open
- 影响：中
- 缓解：固定任务卡 + 防跑偏提示词 + 每日回顾

---

## 11. 完成定义（Definition of Done）

某个功能只有满足以下全部条件才算“完成”：

1. 代码实现已合入本地主分支
2. 相关测试通过
3. 文档已更新（至少更新本文件进展记录）
4. 风险项已标注（若有）
5. 可用一条命令或一个页面完成验证

---

## 12. 给未来自己的建议（单人 + AI）

1. 不要一次给 AI 太大任务，拆到 0.5-1 天粒度。
2. 每天只推进一个核心里程碑，不追求“同时开花”。
3. 先有可运行最小版本，再追求优雅重构。
4. 把“文档更新”当成功能开发的一部分，而不是额外负担。
