# Hello-Agent 对齐 CoPaw Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不重写现有 `graph-core`/`agent-core` 执行内核的前提下，16 周内补齐 CoPaw 关键能力：渠道接入、cron 自动化、skills 生命周期、Hub 导入、workspace 迁移与 CLI 运维入口。

**Architecture:** 以 `Agent-Studio` 为主战场新增“接入层（channels + cron + skills runtime）”和“运维层（hub/workspace/cli）”，继续复用现有 `StreamController` + `AgentExecutionService` 作为统一执行入口；前端在现有 `MainLayout` 下增量扩展管理页，不拆现有聊天主流程。

**Tech Stack:** Java 21, Spring Boot, MyBatis, PostgreSQL, Vue 3, Pinia, TypeScript, Vite, JUnit 5, Mockito

---

## 0. 基线评审（基于当前仓库）

### 0.1 已有可复用能力

1. 执行主链路：`Agent-Studio/src/main/java/cn/ts/web/session/controller/StreamController.java` + `Agent-Studio/src/main/java/cn/ts/web/agent/service/AgentExecutionService.java`
2. 统一响应模型：`Agent-Studio/src/main/java/cn/ts/web/shared/response/Result.java`
3. Skills 只读能力：`Agent-Studio/src/main/java/cn/ts/web/skills/controller/SkillController.java` + `Agent-Studio/src/main/java/cn/ts/web/skills/service/SkillRegistryService.java`
4. 前端 Skills 管理页（只读）：`frontend/src/views/agent/SkillManagement.vue`、`frontend/src/stores/skill.ts`、`frontend/src/api/skill.ts`
5. 数据层风格：`Agent-Studio/src/main/resources/db/schema.sql` + 注解式 MyBatis Mapper（如 `ModelConfigMapper`）

### 0.2 当前缺口（必须补齐）

1. 缺少渠道插件层（channel abstraction/registry/runtime）
2. 缺少 cron 任务中心（数据模型、调度执行、运行记录）
3. skills 只有索引与读取，缺少 create/enable/disable/delete
4. 缺少 skills Hub（GitHub URL）导入链路
5. 缺少 workspace 导入导出
6. 缺少 CLI 运维命令

---

## 1. 里程碑与绝对日期排期（16 周）

| 里程碑 | 周期 | 绝对日期 | 目标 |
|---|---|---|---|
| M0 | Sprint 1 | 2026-03-02 ~ 2026-03-15 | 方案冻结、数据模型冻结、API 草案冻结 |
| M1 | Sprint 2-4 | 2026-03-16 ~ 2026-04-26 | P0：渠道框架 + 首个渠道 + cron + skills 生命周期 |
| M2 | Sprint 5-6 | 2026-04-27 ~ 2026-05-24 | P1：Hub 导入 + workspace 导入导出 |
| M3 | Sprint 7-8 | 2026-05-25 ~ 2026-06-21 | P2：CLI + 安全审计补齐 + 压测与发布 |

---

## 2. 详细实施任务（TDD + 小步提交）

### Task 1: 渠道配置数据模型与管理 API（P0）

**Files:**
- Create: `Agent-Studio/src/main/java/cn/ts/web/channel/entity/ChannelConfigEntity.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/channel/dto/ChannelConfigDTO.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/channel/mapper/ChannelConfigMapper.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/channel/service/ChannelConfigService.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/channel/controller/ChannelManagementController.java`
- Modify: `Agent-Studio/src/main/resources/db/schema.sql`
- Test: `Agent-Studio/src/test/java/cn/ts/web/controller/ChannelManagementControllerTest.java`
- Test: `Agent-Studio/src/test/java/cn/ts/web/service/ChannelConfigServiceTest.java`

**Step 1: Write the failing test**

```java
@Test
void testCreateChannelConfig_ReturnsCreatedConfig() throws Exception {
    // POST /api/channels with valid config should return Result.success(data)
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl Agent-Studio -Dtest=ChannelManagementControllerTest test`
Expected: FAIL with class or endpoint missing

**Step 3: Write minimal implementation**

```java
@RestController
@RequestMapping("/api/channels")
public class ChannelManagementController {
    @PostMapping
    public Result<ChannelConfigDTO> create(@RequestBody ChannelConfigDTO dto) { ... }
}
```

**Step 4: Run tests to verify pass**

Run: `mvn -pl Agent-Studio -Dtest=ChannelManagementControllerTest,ChannelConfigServiceTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add Agent-Studio/src/main/java/cn/ts/web/channel Agent-Studio/src/main/resources/db/schema.sql Agent-Studio/src/test/java/cn/ts/web/controller/ChannelManagementControllerTest.java Agent-Studio/src/test/java/cn/ts/web/service/ChannelConfigServiceTest.java
git commit -m "feat(agent-studio): 新增渠道配置模型与管理API"
```

### Task 2: 渠道运行时框架与首个渠道适配（P0）

**Files:**
- Create: `Agent-Studio/src/main/java/cn/ts/web/channel/runtime/BaseChannel.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/channel/runtime/ChannelRegistry.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/channel/runtime/ChannelRuntimeManager.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/channel/runtime/ChannelMessageDispatcher.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/channel/adapters/dingtalk/DingTalkChannelAdapter.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/channel/controller/ChannelWebhookController.java`
- Modify: `Agent-Studio/src/main/java/cn/ts/web/agent/service/AgentExecutionService.java`
- Test: `Agent-Studio/src/test/java/cn/ts/web/channel/runtime/ChannelRuntimeManagerTest.java`
- Test: `Agent-Studio/src/test/java/cn/ts/web/channel/adapters/dingtalk/DingTalkChannelAdapterTest.java`

**Step 1: Write the failing test**

```java
@Test
void startEnabledChannels_ShouldRegisterAndStartAdapters() {
    // Given enabled channel configs, runtime manager should start adapters
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl Agent-Studio -Dtest=ChannelRuntimeManagerTest test`
Expected: FAIL

**Step 3: Write minimal implementation**

```java
public interface BaseChannel {
    String channelType();
    void start();
    void stop();
    boolean healthy();
}
```

**Step 4: Run tests to verify pass**

Run: `mvn -pl Agent-Studio -Dtest=ChannelRuntimeManagerTest,DingTalkChannelAdapterTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add Agent-Studio/src/main/java/cn/ts/web/channel Agent-Studio/src/main/java/cn/ts/web/agent/service/AgentExecutionService.java Agent-Studio/src/test/java/cn/ts/web/channel
git commit -m "feat(agent-studio): 新增渠道运行时框架与钉钉适配骨架"
```

### Task 3: cron 任务中心（P0）

**Files:**
- Create: `Agent-Studio/src/main/java/cn/ts/web/cron/entity/CronJobEntity.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/cron/entity/CronJobRunEntity.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/cron/mapper/CronJobMapper.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/cron/mapper/CronJobRunMapper.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/cron/service/CronJobService.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/cron/service/CronSchedulerService.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/cron/controller/CronManagementController.java`
- Modify: `Agent-Studio/src/main/resources/db/schema.sql`
- Modify: `Agent-Studio/src/main/resources/application.yml`
- Test: `Agent-Studio/src/test/java/cn/ts/web/cron/service/CronJobServiceTest.java`
- Test: `Agent-Studio/src/test/java/cn/ts/web/cron/service/CronSchedulerServiceTest.java`

**Step 1: Write the failing test**

```java
@Test
void createAndRunNow_ShouldPersistRunRecord() {
    // create job, trigger run-now, assert cron_job_run written
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl Agent-Studio -Dtest=CronJobServiceTest test`
Expected: FAIL

**Step 3: Write minimal implementation**

```java
@PostMapping("/{id}/run")
public Result<Void> runNow(@PathVariable Long id) {
    cronJobService.runNow(id);
    return Result.success();
}
```

**Step 4: Run tests to verify pass**

Run: `mvn -pl Agent-Studio -Dtest=CronJobServiceTest,CronSchedulerServiceTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add Agent-Studio/src/main/java/cn/ts/web/cron Agent-Studio/src/main/resources/db/schema.sql Agent-Studio/src/main/resources/application.yml Agent-Studio/src/test/java/cn/ts/web/cron
git commit -m "feat(agent-studio): 实现cron任务中心与运行记录"
```

### Task 4: skills 生命周期管理 API（P0）

**Files:**
- Create: `Agent-Studio/src/main/java/cn/ts/web/skills/service/SkillLifecycleService.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/skills/dto/SkillLifecycleRequest.java`
- Modify: `Agent-Studio/src/main/java/cn/ts/web/skills/controller/SkillController.java`
- Modify: `Agent-Studio/src/main/java/cn/ts/web/skills/service/SkillRegistryService.java`
- Modify: `Agent-Studio/src/main/resources/application.yml`
- Test: `Agent-Studio/src/test/java/cn/ts/web/skills/service/SkillLifecycleServiceTest.java`
- Test: `Agent-Studio/src/test/java/cn/ts/web/controller/SkillControllerTest.java`

**Step 1: Write the failing test**

```java
@Test
void enableSkill_ShouldMoveSkillToActiveAndReindex() {
    // enable endpoint should activate skill and refresh registry
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl Agent-Studio -Dtest=SkillLifecycleServiceTest test`
Expected: FAIL

**Step 3: Write minimal implementation**

```java
@PostMapping("/{skillId}/enable")
public Result<Void> enable(@PathVariable String skillId) {
    lifecycleService.enable(skillId);
    return Result.success();
}
```

**Step 4: Run tests to verify pass**

Run: `mvn -pl Agent-Studio -Dtest=SkillLifecycleServiceTest,SkillControllerTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add Agent-Studio/src/main/java/cn/ts/web/skills Agent-Studio/src/main/resources/application.yml Agent-Studio/src/test/java/cn/ts/web/skills Agent-Studio/src/test/java/cn/ts/web/controller/SkillControllerTest.java
git commit -m "feat(agent-studio): 增加skills生命周期管理接口"
```

### Task 5: skills Hub 导入（GitHub URL，P1）

**Files:**
- Create: `Agent-Studio/src/main/java/cn/ts/web/skills/service/SkillHubImportService.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/skills/dto/SkillImportRequest.java`
- Modify: `Agent-Studio/src/main/java/cn/ts/web/skills/controller/SkillController.java`
- Modify: `Agent-Studio/src/main/java/cn/ts/web/infra/tempfile/service/TemporaryFileService.java`
- Test: `Agent-Studio/src/test/java/cn/ts/web/skills/service/SkillHubImportServiceTest.java`

**Step 1: Write the failing test**

```java
@Test
void importFromGithubZip_ShouldRejectZipSlipEntry() {
    // malicious zip entry ../ should be rejected
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl Agent-Studio -Dtest=SkillHubImportServiceTest test`
Expected: FAIL

**Step 3: Write minimal implementation**

```java
public ImportResult importFromGithub(String url, boolean overwrite, boolean enableAfterImport) {
    // download zip -> safe extract -> validate SKILL.md -> optional enable
}
```

**Step 4: Run tests to verify pass**

Run: `mvn -pl Agent-Studio -Dtest=SkillHubImportServiceTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add Agent-Studio/src/main/java/cn/ts/web/skills Agent-Studio/src/main/java/cn/ts/web/infra/tempfile/service/TemporaryFileService.java Agent-Studio/src/test/java/cn/ts/web/skills/service/SkillHubImportServiceTest.java
git commit -m "feat(agent-studio): 支持skills Hub GitHub URL导入"
```

### Task 6: workspace 导入导出（P1）

**Files:**
- Create: `Agent-Studio/src/main/java/cn/ts/web/workspace/service/WorkspaceArchiveService.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/workspace/controller/WorkspaceController.java`
- Create: `Agent-Studio/src/main/java/cn/ts/web/workspace/dto/WorkspaceImportRequest.java`
- Modify: `Agent-Studio/src/main/resources/application.yml`
- Test: `Agent-Studio/src/test/java/cn/ts/web/workspace/service/WorkspaceArchiveServiceTest.java`
- Test: `Agent-Studio/src/test/java/cn/ts/web/controller/WorkspaceControllerTest.java`

**Step 1: Write the failing test**

```java
@Test
void exportThenImport_ShouldRestoreSkillsAndConfigFiles() {
    // round-trip archive should preserve expected workspace files
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl Agent-Studio -Dtest=WorkspaceArchiveServiceTest test`
Expected: FAIL

**Step 3: Write minimal implementation**

```java
@GetMapping("/export")
public ResponseEntity<Resource> exportWorkspace() { ... }
```

**Step 4: Run tests to verify pass**

Run: `mvn -pl Agent-Studio -Dtest=WorkspaceArchiveServiceTest,WorkspaceControllerTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add Agent-Studio/src/main/java/cn/ts/web/workspace Agent-Studio/src/main/resources/application.yml Agent-Studio/src/test/java/cn/ts/web/workspace Agent-Studio/src/test/java/cn/ts/web/controller/WorkspaceControllerTest.java
git commit -m "feat(agent-studio): 增加workspace导入导出能力"
```

### Task 7: 前端管理页扩展（channels/cron/skills lifecycle）

**Files:**
- Create: `frontend/src/api/channel.ts`
- Create: `frontend/src/api/cron.ts`
- Create: `frontend/src/stores/channel.ts`
- Create: `frontend/src/stores/cron.ts`
- Create: `frontend/src/views/agent/ChannelManagement.vue`
- Create: `frontend/src/views/agent/CronManagement.vue`
- Modify: `frontend/src/views/agent/SkillManagement.vue`
- Modify: `frontend/src/stores/skill.ts`
- Modify: `frontend/src/api/skill.ts`
- Modify: `frontend/src/layouts/MainLayout.vue`
- Test: `frontend/src/types/channel.ts`
- Test: `frontend/src/types/cron.ts`

**Step 1: Write the failing build check**

```ts
// add new API typing usage in view so missing api/store will fail build
```

**Step 2: Run check to verify it fails**

Run: `cd frontend && npm run build`
Expected: FAIL with TS cannot find module/type errors

**Step 3: Write minimal implementation**

```ts
export async function fetchChannels(): Promise<ChannelConfig[]> {
  const res = await fetch(`${API_BASE}/api/channels`)
  ...
}
```

**Step 4: Run check to verify pass**

Run: `cd frontend && npm run build`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/src/api frontend/src/stores frontend/src/views/agent frontend/src/layouts/MainLayout.vue frontend/src/types
git commit -m "feat(frontend): 新增渠道与cron管理页并扩展skills生命周期交互"
```

### Task 8: CLI 命令集（P2）

**Files:**
- Create: `Agent-Studio/src/main/java/cn/ts/cli/CliApplication.java`
- Create: `Agent-Studio/src/main/java/cn/ts/cli/command/InitCommand.java`
- Create: `Agent-Studio/src/main/java/cn/ts/cli/command/AppCommand.java`
- Create: `Agent-Studio/src/main/java/cn/ts/cli/command/ChannelsCommand.java`
- Create: `Agent-Studio/src/main/java/cn/ts/cli/command/SkillsCommand.java`
- Create: `Agent-Studio/src/main/java/cn/ts/cli/command/CronCommand.java`
- Create: `Agent-Studio/src/main/java/cn/ts/cli/command/EnvCommand.java`
- Modify: `Agent-Studio/pom.xml`
- Test: `Agent-Studio/src/test/java/cn/ts/cli/CliApplicationTest.java`

**Step 1: Write the failing test**

```java
@Test
void initCommand_ShouldGenerateMinimalConfig() {
    // hello-agent init should create baseline env/config files
}
```

**Step 2: Run test to verify it fails**

Run: `mvn -pl Agent-Studio -Dtest=CliApplicationTest test`
Expected: FAIL

**Step 3: Write minimal implementation**

```java
@Command(name = "init")
public class InitCommand implements Runnable { ... }
```

**Step 4: Run tests to verify pass**

Run: `mvn -pl Agent-Studio -Dtest=CliApplicationTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add Agent-Studio/src/main/java/cn/ts/cli Agent-Studio/pom.xml Agent-Studio/src/test/java/cn/ts/cli/CliApplicationTest.java
git commit -m "feat(agent-studio): 增加CLI基础命令集"
```

### Task 9: 安全、可观测、回归与发布收敛（P2）

**Files:**
- Modify: `Agent-Studio/src/main/resources/application.yml`
- Modify: `Agent-Studio/src/main/java/cn/ts/web/shared/config/WebConfig.java`
- Modify: `Agent-Studio/src/main/java/cn/ts/web/shared/exception/GlobalExceptionHandler.java`
- Modify: `docs/`（发布手册与运维手册）
- Test: `Agent-Studio/src/test/java/cn/ts/web/controller/*`（回归补测）

**Step 1: Write the failing regression checklist**

```text
channels/cron/skills/hub/workspace 关键接口回归清单
```

**Step 2: Run full backend tests**

Run: `mvn test`
Expected: identify failures and flaky cases

**Step 3: Fix minimal regressions and hardening**

```java
// normalize error mapping to Result.error(code, message)
```

**Step 4: Verify all quality gates**

Run: `mvn test && cd frontend && npm run build`
Expected: PASS

**Step 5: Commit**

```bash
git add Agent-Studio/src/main/resources/application.yml Agent-Studio/src/main/java/cn/ts/web/shared docs
git commit -m "chore: 完成发布前安全与回归收敛"
```

---

## 3. Sprint 对应任务映射

| Sprint | 日期 | 任务 |
|---|---|---|
| Sprint 1 | 2026-03-02 ~ 2026-03-15 | Task 1 设计冻结 + schema/API 冻结 |
| Sprint 2 | 2026-03-16 ~ 2026-03-29 | Task 1 + Task 2 |
| Sprint 3 | 2026-03-30 ~ 2026-04-12 | Task 3 |
| Sprint 4 | 2026-04-13 ~ 2026-04-26 | Task 4 + P0 联调 |
| Sprint 5 | 2026-04-27 ~ 2026-05-10 | Task 5 |
| Sprint 6 | 2026-05-11 ~ 2026-05-24 | Task 6 + Task 7 |
| Sprint 7 | 2026-05-25 ~ 2026-06-07 | Task 8 |
| Sprint 8 | 2026-06-08 ~ 2026-06-21 | Task 9 + 发布准备 |

---

## 4. 完成定义（DoD）

1. 功能 DoD
- 至少 2 个渠道可稳定收发并触发 Agent 执行
- cron 任务支持 CRUD/启停/立即执行/运行记录
- skills 支持 create/enable/disable/delete/import
- workspace 支持导入导出且有路径安全校验

2. 质量 DoD
- `mvn test` 全量通过
- `cd frontend && npm run build` 通过
- 无 P0/P1 阻断缺陷
- 文档、运维手册、回归清单齐全

---

## 5. 依赖与风险前置约束

1. 先后顺序约束
- Task 1 必须先于 Task 2/3
- Task 4 必须先于 Task 5 与前端 Skills 改造
- Task 7 依赖 Task 1/3/4 的 API 稳定

2. 风险闸门
- 渠道 SDK 不稳定：先以 adapter + mock contract test 解耦
- cron 与聊天争用：独立执行器 + 并发上限
- Hub 导入安全：zip slip 防护 + 来源白名单 + 审计日志
- 回归面扩大：每个 Task 均强制新增对应测试

3. 资源建议
- 后端 2 人、前端 1 人、测试 0.5 人（最小可执行配置）

---

## 6. 执行注意事项（给执行人）

1. 每个 Task 严格按 `Failing Test -> Minimal Code -> Pass -> Commit` 执行
2. 提交信息必须中文，建议使用 `feat:` / `fix:` / `chore:` 前缀
3. 不做跨任务重构；发现问题先记入风险清单，不在当前 Task 扩 scope
4. 每个 Sprint 结束更新本文件的“实际完成/风险变化”
