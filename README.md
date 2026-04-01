<div align="center">

# Hello-Agent

**基于 Java 21 和 Spring Boot 3.3 的 AI Agent 框架**

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3.5-42b883)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178c6)](https://www.typescriptlang.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

一个功能完整的 AI Agent 框架，参考 Spring AI 设计理念，支持状态图执行引擎、ReAct Agent 模式、MCP 集成和检查点系统。

</div>

---

## 目录

- [特性](#特性)
- [架构概览](#架构概览)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [模块说明](#模块说明)
- [配置指南](#配置指南)
- [API 文档](#api-文档)
- [开发指南](#开发指南)
- [贡献指南](#贡献指南)

---

## 特性

### 核心功能

- **状态图执行引擎** - DSL 风格的 API 定义节点和边，支持同步/响应式流式执行
- **ReAct Agent 模式** - 组合 LLM 节点和工具节点，实现推理-行动循环
- **MCP 集成** - 支持 Model Context Protocol，动态加载外部工具
- **检查点系统** - 状态持久化和恢复，支持长任务中断恢复
- **响应式执行** - 基于 Project Reactor 的流式处理，支持 SSE 场景
- **人机协作** - 钩子机制支持人工在环中断和审批恢复
- **可观测性** - Micrometer 集成，支持 Prometheus 指标导出

### 高级特性

- **RAG 功能** - 基于 PostgreSQL + pgvector 的向量存储和相似度检索
- **会话管理** - Session 和 Checkpoint 分离设计，支持多 Agent 切换
- **执行记录** - 统一的 LLM 和工具调用记录系统
- **重试机制** - 指数退避策略的自动重试
- **文档引用** - RAG 查询结果的文档引用系统

---

## 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                         Hello-Agent                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  Frontend    │  │ Agent-Studio │  │     PostgreSQL       │  │
│  │  (Vue 3)     │◄─┤  (Web App)   │◄─┤  (Session/Checkpoint)│  │
│  └──────────────┘  └──────┬───────┘  └──────────────────────┘  │
│                           │                                       │
│                  ┌────────▼────────┐                             │
│                  │  agent-core     │                             │
│                  │  - ReactAgent   │                             │
│                  │  - LLMNode      │                             │
│                  │  - ToolNode     │                             │
│                  │  - McpManager   │                             │
│                  └────────┬────────┘                             │
│                           │                                       │
│                  ┌────────▼────────┐                             │
│                  │   graph-core    │                             │
│                  │  - StateGraph   │                             │
│                  │  - GraphRunner  │                             │
│                  │  - Checkpoint   │                             │
│                  └─────────────────┘                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 编程语言 |
| Spring Boot | 3.3.0 | 应用框架 |
| Spring AI | 1.1.2 | AI 集成 |
| Project Reactor | - | 响应式编程 |
| MyBatis | - | ORM 框架 |
| PostgreSQL | - | 数据库 |
| pgvector | - | 向量存储 |
| Lombok | - | 代码简化 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.24 | 前端框架 |
| TypeScript | 5.9 | 类型系统 |
| Vite | 7.2.4 | 构建工具 |
| Pinia | 3.0.4 | 状态管理 |
| Tailwind CSS | 4.1.18 | UI 框架 |
| Vue Flow | 1.47.0 | 流程图可视化 |

---

## 快速开始

### 环境要求

- **JDK**: 21+
- **Maven**: 3.8+
- **Node.js**: 18+
- **PostgreSQL**: 14+ (with pgvector)

### 1. 克隆仓库

```bash
git clone https://github.com/yourusername/Hello-Agent.git
cd Hello-Agent
```

### 2. 配置数据库

启动 PostgreSQL 数据库（使用 Docker）：

```bash
docker-compose up -d
```

或手动创建数据库：

```sql
CREATE DATABASE agent_studio_db;
CREATE EXTENSION vector;
```

### 3. 配置应用

编辑 `agent-studio/src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/agent_studio_db
    username: your_username
    password: your_password

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
```

### 4. 启动后端

```bash
# 编译整个项目
mvn clean compile

# 启动 Agent-Studio 应用
cd agent-studio
mvn spring-boot:run
```

后端将在 `http://localhost:8080` 启动。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端将在 `http://localhost:5173` 启动。

---

## 模块说明

### graph-core

图执行引擎核心模块，提供状态图定义、编译和执行能力。

```java
StateGraph graph = new StateGraph()
    .addNode("node1", state -> { ... })
    .addEdge("node1", "node2")
    .setEntryPoint("node1")
    .compile();
```

### agent-core

Agent 实现模块，包含 ReAct Agent、LLM 节点和工具节点。

```java
ReactAgent agent = ReactAgent.builder()
    .name("my-agent")
    .chatModel(chatModel)
    .tools(tools)
    .maxIterations(10)
    .build();
```

### agent-studio

Web 应用模块，提供 REST API 和 SSE 流式端点。

- **Agent 管理**: 创建、更新、删除 Agent 配置
- **执行服务**: Agent 注册和流式执行
- **MCP 管理**: MCP 服务器连接管理
- **RAG 服务**: 文档上传和知识库查询
- **会话管理**: 会话创建、恢复和摘要

### frontend

Vue 3 前端应用，提供可视化界面。

- **Agent 管理界面**: Agent 配置和管理
- **聊天界面**: 实时对话和流式响应
- **MCP 管理界面**: MCP 服务器配置
- **RAG 查询界面**: 知识库上传和查询
- **时间线视图**: Agent 执行流程可视化

---

## 配置指南

### Agent 执行配置

```yaml
agent:
  execution:
    timeout: 300s              # 执行超时时间
    heartbeat-interval: 30s    # 心跳间隔
    max-title-length: 15        # 标题最大长度
    default-max-iterations: 100  # 默认最大迭代次数
    debug-mode: false           # 调试模式
    retry:
      enabled: true             # 是否启用重试
      max-retries: 3            # 最大重试次数
      initial-backoff: 1s       # 初始退避时间
      backoff-multiplier: 2.0   # 退避时间倍数
      max-backoff: 30s          # 最大退避时间
```

### MCP 配置

```yaml
mcp:
  manager:
    health-check-interval: 1m    # 健康检查间隔
    default-timeout: 30s         # 默认连接超时时间
    auto-connect-on-startup: true # 启动时自动连接
  servers:
    servers:
      - name: amap
        type: STDIO
        description: 高德地图 MCP 服务
        stdio:
          command: ${nodejs.npx-path}
          args:
            - "-y"
            - "@amap/amap-maps-mcp-server"
          env:
            AMAP_MAPS_API_KEY: ${api.keys.amap}
```

### RAG 配置

```yaml
rag:
  text-splitter:
    chunk-size: 500             # 文本块大小
    chunk-overlap: 50           # 块重叠大小
    min-chunk-size: 100         # 最小块大小
  vector-store:
    similarity-threshold: 0.7   # 相似度阈值
    top-k: 5                    # 返回结果数量
```

---

## API 文档

### Agent 管理

| 方法 | 端点 | 描述 |
|------|------|------|
| GET | `/api/agents` | 获取所有 Agent |
| POST | `/api/agents` | 创建 Agent |
| GET | `/api/agents/{id}` | 获取 Agent 详情 |
| PUT | `/api/agents/{id}` | 更新 Agent |
| DELETE | `/api/agents/{id}` | 删除 Agent |

### Agent 执行

| 方法 | 端点 | 描述 |
|------|------|------|
| POST | `/api/stream/agent/{agentName}/execute` | 执行 Agent (SSE) |
| POST | `/api/stream/agent/{agentName}/resume` | 恢复执行 |
| GET | `/api/stream/agent/{agentName}/exists` | 检查 Agent 是否存在 |
| GET | `/api/stream/agents` | 获取所有已注册 Agent |

### 会话管理

| 方法 | 端点 | 描述 |
|------|------|------|
| GET | `/api/sessions` | 获取所有会话 |
| POST | `/api/sessions` | 创建新会话 |
| GET | `/api/sessions/{sessionId}` | 获取会话详情 |
| DELETE | `/api/sessions/{sessionId}` | 删除会话 |
| GET | `/api/sessions/{sessionId}/summary` | 获取会话摘要 |

### MCP 管理

| 方法 | 端点 | 描述 |
|------|------|------|
| GET | `/api/mcp/servers` | 获取所有 MCP 服务器 |
| POST | `/api/mcp/servers` | 添加 MCP 服务器 |
| DELETE | `/api/mcp/servers/{name}` | 删除 MCP 服务器 |

### RAG 功能

| 方法 | 端点 | 描述 |
|------|------|------|
| POST | `/api/rag/upload` | 上传文档到知识库 |
| POST | `/api/rag/query` | 查询知识库 |
| GET | `/api/rag/knowledge-bases` | 获取知识库列表 |
| DELETE | `/api/rag/knowledge-bases/{id}` | 删除知识库 |

### 可观测性

| 方法 | 端点 | 描述 |
|------|------|------|
| GET | `/actuator/health` | 健康检查 |
| GET | `/actuator/prometheus` | Prometheus 指标 |
| GET | `/actuator/metrics` | 指标列表 |

---

## 开发指南

### 添加新的 Agent

1. 在数据库中创建 Agent 配置（通过 API 或直接插入）
2. Agent 会自动注册到 `AgentExecutionService`
3. 使用 `/api/stream/agent/{agentName}/execute` 执行

### 添加新的工具

1. 创建工具类并使用 `@Tool` 注解（Spring AI）或实现 MCP 客户端
2. 通过 API 注册工具定义
3. 将工具关联到 Agent 配置

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=StateGraphTest

# 运行特定测试方法
mvn test -Dtest=StateGraphTest#testAddNode

# 生成覆盖率报告
mvn clean test jacoco:report
# 报告位于 target/site/jacoco/index.html
```

### 代码规范

- **Java 版本**: 21
- **使用 Lombok** 简化代码
- **异常处理**: 使用 `GraphException` 及其子类
- **响应式编程**: 使用 Reactor Core
- **日志记录**: 使用 SLF4J

### Git 提交规范

```
<type>: <subject>

<body>
```

**类型 (type)**:
- `feat`: 新功能
- `fix`: 修复 bug
- `refactor`: 重构代码
- `chore`: 构建/工具链相关
- `docs`: 文档更新
- `test`: 测试相关

**示例**:
```
feat: 添加 MCP 工具自动发现功能

实现了基于注解的工具自动发现机制，
支持 Spring AI @Tool 注解和 MCP 客户端工具。
```

---

## 项目结构

```
Hello-Agent/
├── graph-core/           # 图执行引擎核心
├── agent-core/           # Agent 实现
├── agent-studio/         # Web 应用
├── frontend/             # Vue 3 前端
├── docs/                 # 文档
├── pom.xml              # Maven 父配置
├── CLAUDE.md            # 项目开发指南
└── README.md            # 本文件
```

---

## 许可证

本项目采用 [MIT](LICENSE) 许可证。

---

## 联系方式

- 项目主页: [https://github.com/yourusername/Hello-Agent](https://github.com/yourusername/Hello-Agent)
- 问题反馈: [GitHub Issues](https://github.com/yourusername/Hello-Agent/issues)

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给一个 Star！**

</div>
