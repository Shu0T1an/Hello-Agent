# Claude Code 核心工具使用指南

本文档汇总了 Claude Code 中最常用的文件操作工具的使用方法。

## 目录

- [Read - 读取文件](#1-read---读取文件)
- [Write - 写入文件](#2-write---写入文件)
- [Edit - 编辑文件](#3-edit---编辑文件)
- [Glob - 文件名模式匹配](#4-glob---文件名模式匹配)
- [Grep - 文件内容搜索](#5-grep---文件内容搜索)
- [工作流示例](#工作流示例)

---

## 1. Read - 读取文件

### 函数签名

```
Read(file_path, offset?, limit?, pages?)
```

### 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `file_path` | string | ✅ | 文件的绝对路径 |
| `offset` | number | ❌ | 从第几行开始读取（默认1） |
| `limit` | number | ❌ | 读取多少行（默认全部） |
| `pages` | string | ❌ | PDF页码范围，如 "1-5"、"3"（仅PDF） |

### 支持的文件类型

- 文本文件（.txt, .md, .yaml, .json, .java, .py 等）
- 图片文件（.png, .jpg, .jpeg 等）
- PDF 文件（.pdf）
- Jupyter Notebook（.ipynb）

### 使用示例

```javascript
// 读取整个文件
Read(file_path="D:/project/src/Main.java")

// 读取前100行
Read(file_path="D:/project/src/Main.java", limit=100)

// 从第50行开始读100行
Read(file_path="D:/project/src/Main.java", offset=50, limit=100)

// 读取PDF第1-5页
Read(file_path="D:/docs/manual.pdf", pages="1-5")

// 读取PDF特定页
Read(file_path="D:/docs/manual.pdf", pages="3")

// 读取图片
Read(file_path="D:/images/screenshot.png")
```

---

## 2. Write - 写入文件

### 函数签名

```
Write(file_path, content)
```

### 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `file_path` | string | ✅ | 文件的绝对路径 |
| `content` | string | ✅ | 要写入的内容 |

### 重要说明

- **创建新文件**：直接使用即可
- **覆盖已存在的文件**：必须先用 Read 工具读取文件内容
- 会完全覆盖原有内容

### 使用示例

```javascript
// 创建新文件
Write(
  file_path="D:/project/config.yaml",
  content="server:\n  port: 8080"
)

// 覆盖已存在的文件（必须先Read）
Write(
  file_path="D:/project/README.md",
  content="# My Project\n\nDescription here..."
)

// 创建JSON配置文件
Write(
  file_path="D:/project/settings.json",
  content='{\n  "name": "my-app",\n  "version": "1.0.0"\n}'
)

// 创建脚本文件
Write(
  file_path="D:/project/scripts/start.sh",
  content="#!/bin/bash\necho 'Starting application...'\njava -jar app.jar"
)
```

---

## 3. Edit - 编辑文件

### 函数签名

```
Edit(file_path, old_string, new_string, replace_all?)
```

### 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `file_path` | string | ✅ | 文件的绝对路径 |
| `old_string` | string | ✅ | 要替换的原始文本 |
| `new_string` | string | ✅ | 替换后的新文本 |
| `replace_all` | boolean | ❌ | 是否替换所有匹配项（默认false） |

### 重要说明

- 编辑前**必须**先用 Read 工具读取文件
- `old_string` 必须在文件中唯一存在（除非使用 `replace_all`）
- 保持原始缩进格式

### 使用示例

```javascript
// 替换单个匹配（最常用）
Edit(
  file_path="D:/project/src/Main.java",
  old_string="public void oldMethod()",
  new_string="public void newMethod()"
)

// 替换所有匹配
Edit(
  file_path="D:/project/src/Main.java",
  old_string="TODO",
  new_string="FIXME",
  replace_all=true
)

// 替换多行内容
Edit(
  file_path="D:/project/config.yaml",
  old_string="server:\n  port: 8080",
  new_string="server:\n  port: 9090\n  host: 0.0.0.0"
)

// 修改方法签名
Edit(
  file_path="D:/project/src/Service.java",
  old_string="public String getData(String id) {",
  new_string="public DataResponse getData(String id) {"
)

// 添加导入语句
Edit(
  file_path="D:/project/src/Main.java",
  old_string="import java.util.List;",
  new_string="import java.util.List;\nimport java.util.ArrayList;"
)
```

---

## 4. Glob - 文件名模式匹配

### 函数签名

```
Glob(pattern, path?)
```

### 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `pattern` | string | ✅ | Glob匹配模式 |
| `path` | string | ❌ | 搜索目录（默认当前目录） |

### 常用模式

| 模式 | 匹配 |
|------|------|
| `*.java` | 当前目录所有Java文件 |
| `**/*.java` | 递归搜索所有Java文件 |
| `src/**/*.ts` | src目录下所有TypeScript文件 |
| `**/*Test.java` | 所有Test结尾的Java文件 |
| `**/config/*.yaml` | 所有config目录下的yaml文件 |
| `**/*.{js,ts}` | 所有js和ts文件 |

### 通配符说明

| 符号 | 含义 |
|------|------|
| `*` | 匹配任意字符（不包括路径分隔符） |
| `**` | 匹配任意目录层级 |
| `?` | 匹配单个字符 |
| `[a-z]` | 匹配字符范围 |
| `{a,b}` | 匹配多个选项 |

### 使用示例

```javascript
// 查找所有Java文件
Glob(pattern="**/*.java")

// 查找所有配置文件
Glob(pattern="**/*.{yaml,yml,json}")

// 在src目录查找所有TypeScript文件
Glob(pattern="**/*.ts", path="D:/project/src")

// 查找所有测试文件
Glob(pattern="**/*Test*.java")

// 查找特定目录的文件
Glob(pattern="**/controller/*.java")

// 查找README文件
Glob(pattern="**/README.md")

// 查找所有图片文件
Glob(pattern="**/*.{png,jpg,jpeg,gif}")
```

---

## 5. Grep - 文件内容搜索

### 函数签名

```
Grep(pattern, path?, glob?, output_mode?, type?, -i?, -C?, -A?, -B?, multiline?)
```

### 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `pattern` | string | ✅ | 正则表达式搜索模式 |
| `path` | string | ❌ | 搜索目录（默认当前目录） |
| `glob` | string | ❌ | 文件类型过滤（如 "*.java"） |
| `output_mode` | string | ❌ | 输出模式：content/files_with_matches/count |
| `type` | string | ❌ | 文件类型（如 "java", "py", "js"） |
| `-i` | boolean | ❌ | 忽略大小写 |
| `-C` | number | ❌ | 匹配行前后各显示的行数 |
| `-A` | number | ❌ | 匹配行后显示的行数 |
| `-B` | number | ❌ | 匹配行前显示的行数 |
| `multiline` | boolean | ❌ | 多行匹配模式 |

### 输出模式

| 模式 | 说明 |
|------|------|
| `content` | 显示匹配行内容（默认） |
| `files_with_matches` | 仅显示包含匹配的文件名 |
| `count` | 显示每个文件的匹配数量 |

### 使用示例

```javascript
// ===== 基础搜索 =====

// 在所有Java文件中搜索方法名
Grep(pattern="functionName", type="java")

// 搜索包含 "Error" 的所有文件
Grep(pattern="Error", output_mode="files_with_matches")

// 统计匹配数量
Grep(pattern="import", type="java", output_mode="count")

// ===== 上下文显示 =====

// 显示匹配行前后各2行
Grep(pattern="TODO", type="java", -C=2)

// 显示匹配行后5行
Grep(pattern="@RestController", type="java", -A=5)

// 显示匹配行前3行
Grep(pattern="class UserService", type="java", -B=3)

// ===== 大小写不敏感 =====

Grep(pattern="TODO", type="java", -i=true, -C=2)

// ===== 正则表达式 =====

// 搜索所有接口定义
Grep(pattern="interface\\s+\\w+", type="java")

// 搜索邮箱地址
Grep(pattern="[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")

// 搜索URL
Grep(pattern="https?://[^\\s]+")

// ===== 多行匹配 =====

Grep(pattern="class \\{[\\s\\S]*?field", multiline=true)

// ===== 在特定目录搜索 =====

Grep(pattern="main", path="D:/project/src")

// 组合使用：在src目录搜索Java文件中的TODO，显示上下文
Grep(
  pattern="TODO",
  path="D:/project/src",
  type="java",
  -C=3
)

// ===== 常见搜索场景 =====

// 搜索Spring注解
Grep(pattern="@(RestController|Service|Repository)", type="java")

// 查找可能的bug
Grep(pattern="TODO|FIXME|XXX|HACK", type="java")

// 查找测试断言
Grep(pattern="assert", type="java", output_mode="files_with_matches")

// 查找日志语句
Grep(pattern="log\\.(debug|info|warn|error)", type="java")
```

---

## 工作流示例

### 场景1：修改配置文件

```javascript
// 步骤1：读取当前配置
Read(file_path="D:/project/application.yml")

// 步骤2：修改配置值
Edit(
  file_path="D:/project/application.yml",
  old_string="port: 8080",
  new_string="port: 9090"
)
```

### 场景2：批量查找和重构代码

```javascript
// 步骤1：查找所有相关文件
Glob(pattern="**/*Service.java")

// 步骤2：在所有Java文件中搜索旧方法名
Grep(pattern="public void processRequest", type="java")

// 步骤3：读取需要修改的文件
Read(file_path="D:/project/src/UserService.java")

// 步骤4：修改方法名
Edit(
  file_path="D:/project/src/UserService.java",
  old_string="public void processRequest()",
  new_string="public void handleRequest()"
)
```

### 场景3：搜索并查看TODO

```javascript
// 步骤1：搜索所有包含TODO的文件
Grep(pattern="TODO", type="java", output_mode="files_with_matches")

// 步骤2：读取特定文件查看详情
Read(file_path="D:/project/src/Service.java")

// 步骤3：查看TODO的上下文
Grep(pattern="TODO", path="D:/project/src/Service.java", -C=5)
```

### 场景4：创建新功能文件

```javascript
// 步骤1：查找同类文件作为参考
Glob(pattern="**/controller/*.java")

// 步骤2：读取参考文件
Read(file_path="D:/project/src/controller/UserController.java")

// 步骤3：创建新文件
Write(
  file_path="D:/project/src/controller/ProductController.java",
  content="package com.example.controller;\n\n// 新的控制器内容..."
)
```

### 场景5：查找并修复问题

```javascript
// 步骤1：搜索所有可能的错误处理
Grep(pattern="catch\\s*\\(\\s*Exception\\s*\\)", type="java")

// 步骤2：读取问题文件
Read(file_path="D:/project/src/Service.java")

// 步骤3：添加更好的错误处理
Edit(
  file_path="D:/project/src/Service.java",
  old_string="catch (Exception e) {\n    // ignore\n}",
  new_string="catch (IOException e) {\n    log.error(\"Failed to process\", e);\n    throw new ServiceException(\"Processing failed\", e);\n}"
)
```

---

## 工具对比

| 工具 | 主要用途 | 是否需要先Read | 适用场景 |
|------|---------|:--------------:|---------|
| **Read** | 读取内容 | ❌ | 查看文件、了解代码 |
| **Write** | 创建/覆盖 | ✅ (覆盖时) | 新建文件、完全重写 |
| **Edit** | 局部修改 | ✅ | 修改代码、配置调整 |
| **Glob** | 查找文件 | ❌ | 发现文件、批量处理 |
| **Grep** | 搜索内容 | ❌ | 代码搜索、定位问题 |

---

## 最佳实践

### 1. 编辑文件前始终先读取
```javascript
// ✅ 正确
Read(file_path="src/Main.java")
Edit(file_path="src/Main.java", ...)

// ❌ 错误（未读取直接编辑）
Edit(file_path="src/Main.java", ...)
```

### 2. 使用 Glob 发现文件
```javascript
// ✅ 先找到文件，再操作
Glob(pattern="**/*.java")
Read(file_path="src/found/File.java")
```

### 3. 使用 Grep 定位问题
```javascript
// ✅ 搜索关键词，再查看详情
Grep(pattern="NullPointerException", type="java", -C=3)
```

### 4. Edit 保持精确匹配
```javascript
// ✅ 包含足够的上下文使old_string唯一
Edit(
  file_path="src/Main.java",
  old_string="public void processData(String id) {",
  new_string="public void processData(String id, String type) {"
)
```

### 5. 使用 replace_all 谨慎
```javascript
// ✅ 确定要替换所有时使用
Edit(file_path="src/config.yaml",
  old_string="true",
  new_string="false",
  replace_all=true
)
```

---

## 快速参考

```javascript
// 查找文件
Glob(pattern="**/*.java")

// 搜索内容
Grep(pattern="keyword", type="java")

// 读取文件
Read(file_path="path/to/file.java")

// 编辑文件
Edit(file_path="path/to/file.java", old="...", new="...")

// 写入文件
Write(file_path="path/to/newfile.txt", content="...")
```

---

**文档版本**: 1.0
**更新日期**: 2026-02-25
