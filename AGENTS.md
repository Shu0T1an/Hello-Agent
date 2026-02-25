# 仓库指南

## 项目结构与模块组织
`Hello-Agent` 是一个多模块 Maven 项目，带有 Vue 前端。
- `graph-core/`: 图运行时、状态管理和执行原语。
- `agent-core/`: Agent 逻辑、工具、钩子、模型集成和 RAG 适配器。
- `Agent-Studio/`: Spring Boot Web 应用（REST/SSE API、服务层、持久化）。
- `frontend/`: Vue 3 + TypeScript + Vite UI（`src/views`、`src/components`、`src/stores`、`src/api`）。
- `docs/`: 设计和使用说明。运行时数据目录如 `uploads/` 和 `postgres_data/` 是非源代码产物。

## 构建、测试和开发命令
除非另有说明，否则从仓库根目录运行。
- `mvn clean install`: 构建所有 Java 模块并运行测试。
- `mvn test`: 执行所有后端单元/集成测试。
- `mvn -pl agent-core test`: 仅运行 `agent-core` 测试。
- `mvn -pl Agent-Studio spring-boot:run`: 在端口 `8080` 启动后端。
- `cd frontend && npm install`: 安装 UI 依赖。
- `cd frontend && npm run dev`: 在端口 `5173` 启动 Vite 开发服务器。
- `cd frontend && npm run build`: 类型检查并生成生产构建。
- `./start-all.ps1`: 在 Windows 上同时启动后端和前端。

## 编码风格与命名规范
- Java: 目标 Java 21，UTF-8，4 空格缩进，包命名空间 `cn.ts...`。
- 类使用 `PascalCase`，方法/字段使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。
- Vue/TypeScript: 组件优先使用 `PascalCase.vue`，工具/组合式函数使用 `camelCase.ts`，API 模块放在 `frontend/src/api` 下。
- 在 `Agent-Studio` 中遵循现有分层（`controller -> service -> mapper/entity`）。

## 测试指南
- 后端测试使用 JUnit 5、Mockito 和 Spring Boot Test。
- 将测试放在各模块的 `src/test/java` 下；测试类命名为 `*Test`（集成测试可使用 `*IntegrationTest`）。
- 保持测试确定性和隔离性；使用 `src/test/resources/application-test.yml` 作为测试配置。
- 在打开 PR 之前，运行 `mvn test`，对于影响 UI 的变更，运行 `cd frontend && npm run build`。

## 提交与 Pull Request 指南
- 遵循仓库历史中显示的约定提交模式：`feat:`、`fix:`、`refactor:`、`docs:`、`chore:` 和可选的作用域如 `feat(frontend): ...`。
- 按模块或关注点保持提交聚焦。
- PR 应包括：目的、受影响的模块、测试证据（命令 + 结果）、关联问题（如有），以及前端 UI 变更的截图/GIF。
- 突出显示配置变更（端口、数据库、API 密钥、MCP 设置），以便审查者在本地重现。
## 提交补充规范
- Git 提交信息必须使用中文（包括 commit 标题与必要说明）。
