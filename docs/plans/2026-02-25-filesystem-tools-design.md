# 文件系统工具设计（Read/Write/Edit/Glob/Grep）

## 1. 背景与目标

为当前 Agent 增加一组本地文件系统工具，覆盖以下能力：

- `Read`：读取文件内容
- `Write`：写入文件（创建或覆盖）
- `Edit`：原地文本替换编辑
- `Glob`：按模式遍历文件
- `Grep`：按正则搜索内容

目标是在现有本地工具机制内落地，不引入 MCP 作为首期依赖。

## 2. 已确认决策

### 2.1 交付形态

- 选择：仅本地工具（不做 MCP 版本）。
- 原因：与现有 `LocalToolScanner -> tool_definition -> Agent 装配` 链路一致，落地成本最低。

### 2.2 目录权限

- 读白名单：`D:\JavaProject\Hello-Agent`
- 写白名单：`D:\JavaProject\Hello-Agent\docs`、`D:\JavaProject\Hello-Agent\uploads`
- 策略：读范围宽于写范围。

### 2.3 Edit 语义

- 仅支持文本替换接口：`Edit(file_path, old_string, new_string, replace_all?)`
- 不提供行号编辑接口。

### 2.4 Grep 技术路线（首期）

- 首期采用 Java 原生实现（不依赖 `ripgrep` 可执行文件）。
- 后续可在同一接口下扩展 `ripgrep` 后端。

## 3. 方案对比与推荐

### 3.1 纯 Java

- 优点：无外部依赖、部署简单、行为可控。
- 缺点：在复杂正则和大仓库场景下性能弱于 `ripgrep`。

### 3.2 纯命令行

- 优点：实现快，搜索能力强。
- 缺点：跨平台和安全治理复杂，错误语义不易统一。

### 3.3 混合（后续可选）

- `Read/Write/Edit/Glob` 用 Java，`Grep` 用 `ripgrep`。
- 作为后续性能增强方案，不作为首期实现。

### 3.4 首期选择

- 选择 3.1（纯 Java）。

## 4. 架构设计

新增本地工具类：

- `cn.ts.web.tool.local.FileSystemTools`

新增核心服务：

- `PathPolicyService`
  - 负责路径规范化、白名单校验、路径越权阻断。
- `FileOpsService`
  - 负责 `Read/Write/Edit` 逻辑。
- `SearchService`
  - 负责 `Glob/Grep` 逻辑。

工具方法负责参数接收和结果封装，业务逻辑下沉到服务层。

## 5. 工具接口契约（对齐 claude-code-tools-guide）

所有工具采用“请求对象入参 + 统一响应结构”。

### 5.1 Read

- 入参：`file_path`、`offset`、`limit`、`pages`
- 约束：
  - 首期只支持文本文件。
  - 若传 `pages`，返回 `UNSUPPORTED_PAGES_FOR_TEXT`。

### 5.2 Write

- 入参：`file_path`、`content`
- 语义：
  - 文件不存在则创建；
  - 文件存在则覆盖。

### 5.3 Edit

- 入参：`file_path`、`old_string`、`new_string`、`replace_all`
- 语义：
  - `replace_all=false`：必须唯一匹配 1 处；
  - `replace_all=true`：替换全部匹配。

### 5.4 Glob

- 入参：`pattern`、`path`
- 语义：
  - `path` 为空时默认仓库根目录。

### 5.5 Grep

- 入参：`pattern`、`path`、`glob`、`output_mode`、`type`、`ignore_case`、`context`、`after_context`、`before_context`、`multiline`
- 首期实现：
  - Java 正则 + 文件遍历。
  - `multiline=true` 时按整文件匹配后映射行号。

## 6. 安全策略

### 6.1 路径校验流程

每次请求必须执行：

1. 路径解析与 `normalize()`
2. 真实路径解析（尽量 `toRealPath(NOFOLLOW_LINKS)`）
3. 白名单 `startsWith` 判定
4. 越权直接拒绝（`PATH_NOT_ALLOWED`）

### 6.2 目录遍历策略

- `Glob/Grep` 遍历时默认不跟随符号链接目录。
- 若解析后真实路径跳出白名单，拒绝执行。

### 6.3 内容与体积限制

- 仅处理 UTF-8 文本文件。
- 检测到二进制文件返回 `BINARY_FILE_NOT_SUPPORTED`。
- 限制单文件最大读取字节和单次返回最大行数，避免响应过大。

## 7. 统一错误模型

### 7.1 通用错误码

- `INVALID_ARGUMENT`
- `PATH_NOT_ALLOWED`
- `IO_ERROR`
- `TIMEOUT`
- `INTERNAL_ERROR`

### 7.2 文件错误码

- `FILE_NOT_FOUND`
- `NOT_A_FILE`
- `NOT_A_DIRECTORY`
- `BINARY_FILE_NOT_SUPPORTED`
- `FILE_TOO_LARGE`

### 7.3 Edit 错误码

- `OLD_STRING_NOT_FOUND`
- `OLD_STRING_NOT_UNIQUE`
- `NO_CHANGES_MADE`

### 7.4 搜索错误码

- `INVALID_REGEX`
- `INVALID_GLOB_PATTERN`
- `UNSUPPORTED_OUTPUT_MODE`
- `UNSUPPORTED_FILE_TYPE`

## 8. 返回结构

统一返回 JSON 字符串：

- `status`：`ok | error`
- `errorCode`：失败时返回
- `message`：人类可读说明
- `data`：工具具体结果

## 9. 测试策略

测试建议放置于 `Agent-Studio/src/test/java`：

- `PathPolicyServiceTest`：白名单、路径穿越、符号链接越权
- `FileOpsServiceTest`：Read/Write/Edit 主流程与错误分支
- `SearchServiceTest`：Glob/Grep 输出模式与边界条件
- `FileSystemToolsIntegrationTest`：`@Tool` 层端到端验证

关键覆盖点：

- `replace_all` 的唯一匹配约束
- 文本与二进制文件区分
- 大文件与返回截断
- `output_mode=content/files_with_matches/count`

## 10. 分阶段实施

### 阶段 1（当前）

- 实现纯 Java 版本 `Read/Write/Edit/Glob/Grep`
- 接入本地工具扫描和现有 Agent 工具列表
- 增加核心单元测试

### 阶段 2（后续扩展）

- 为 `Grep` 增加 `ripgrep` 后端
- 增加配置开关：`java` / `ripgrep` / `auto`
- 基于实际仓库规模评估性能收益

