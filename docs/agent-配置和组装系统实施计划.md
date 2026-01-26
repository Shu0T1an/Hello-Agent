# Agent 配置和组装系统 - 实施计划

## 概述

实现一个完整的 Agent 配置和组装系统，支持用户通过 Web 界面创建、配置和管理个性化 Agent。

### 核心功能
1. **Agent 配置管理** - 动态创建和配置 Agent（模型、Prompt、工具）
2. **工具市场** - 统一管理本地工具和 MCP 工具
3. **模型市场** - 管理多个模型提供商和配置
4. **动态组装** - 基于配置自动组装 ReActAgent

### 用户决策
- 本地工具：自动扫描注册
- MCP 工具：自动同步到工具市场
- API 密钥：数据库加密存储
- 配置生效：热重载

---

## 数据库设计

### 核心表结构

```sql
-- 1. Agent 配置表
CREATE TABLE agent_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_name VARCHAR(100) NOT NULL UNIQUE COMMENT 'Agent名称',
    display_name VARCHAR(200) NOT NULL COMMENT '显示名称',
    description TEXT COMMENT 'Agent描述',
    model_id BIGINT NOT NULL COMMENT '关联模型ID',
    system_prompt TEXT COMMENT '系统提示词',
    max_iterations INT DEFAULT 10 COMMENT '最大迭代次数',
    temperature DECIMAL(3,2) DEFAULT 0.70 COMMENT '温度参数',
    enable_streaming BOOLEAN DEFAULT TRUE COMMENT '是否启用流式输出',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    created_by VARCHAR(50) DEFAULT 'system' COMMENT '创建者',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_agent_name (agent_name),
    INDEX idx_is_active (is_active),
    FOREIGN KEY (model_id) REFERENCES model_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent配置表';

-- 2. 模型配置表（API 密钥加密存储）
CREATE TABLE model_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_name VARCHAR(100) NOT NULL UNIQUE COMMENT '模型名称',
    display_name VARCHAR(200) NOT NULL COMMENT '显示名称',
    provider VARCHAR(50) NOT NULL COMMENT '提供商',
    model_id VARCHAR(100) NOT NULL COMMENT '模型ID',
    base_url VARCHAR(500) COMMENT 'API基础URL',
    api_key_encrypted VARCHAR(500) COMMENT '加密后的API密钥',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否可用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_provider (provider),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表';

-- 3. 工具定义表
CREATE TABLE tool_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tool_name VARCHAR(100) NOT NULL UNIQUE COMMENT '工具名称',
    display_name VARCHAR(200) NOT NULL COMMENT '显示名称',
    description TEXT COMMENT '工具描述',
    tool_type ENUM('LOCAL', 'MCP') NOT NULL DEFAULT 'LOCAL' COMMENT '工具类型',
    class_name VARCHAR(200) COMMENT 'Java类名（本地工具）',
    mcp_connection_name VARCHAR(100) COMMENT 'MCP连接名称（MCP工具）',
    mcp_tool_name VARCHAR(100) COMMENT 'MCP工具名称',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否可用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tool_type (tool_type),
    INDEX idx_is_active (is_active),
    INDEX idx_mcp_connection (mcp_connection_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具定义表';

-- 4. Agent-工具关联表
CREATE TABLE agent_tool_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_config_id BIGINT NOT NULL COMMENT 'Agent配置ID',
    tool_definition_id BIGINT NOT NULL COMMENT '工具定义ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_agent_tool (agent_config_id, tool_definition_id),
    FOREIGN KEY (agent_config_id) REFERENCES agent_config(id) ON DELETE CASCADE,
    FOREIGN KEY (tool_definition_id) REFERENCES tool_definition(id) ON DELETE CASCADE,
    INDEX idx_agent_config (agent_config_id),
    INDEX idx_tool_definition (tool_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent工具关联表';

-- 5. MCP 连接配置表（持久化现有配置）
CREATE TABLE mcp_connection_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    connection_name VARCHAR(100) NOT NULL UNIQUE COMMENT '连接名称',
    description TEXT COMMENT '连接描述',
    connection_type ENUM('STDIO', 'SSE') NOT NULL COMMENT '连接类型',
    command VARCHAR(500) COMMENT '命令（STDIO）',
    args JSON COMMENT '参数列表（STDIO）',
    env JSON COMMENT '环境变量（STDIO）',
    url VARCHAR(500) COMMENT 'URL（SSE）',
    timeout_seconds INT DEFAULT 30 COMMENT '超时时间（秒）',
    auto_reconnect BOOLEAN DEFAULT TRUE COMMENT '是否自动重连',
    max_retries INT DEFAULT 3 COMMENT '最大重试次数',
    retry_interval_seconds INT DEFAULT 5 COMMENT '重试间隔（秒）',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_connection_type (connection_type),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP连接配置表';
```

---

## 关键文件路径

### 后端文件

#### 实体类
- `Agent-Studio/src/main/java/cn/ts/web/entity/AgentConfigEntity.java`
- `Agent-Studio/src/main/java/cn/ts/web/entity/ModelConfigEntity.java`
- `Agent-Studio/src/main/java/cn/ts/web/entity/ToolDefinitionEntity.java`
- `Agent-Studio/src/main/java/cn/ts/web/entity/AgentToolMappingEntity.java`
- `Agent-Studio/src/main/java/cn/ts/web/entity/McpConnectionConfigEntity.java`

#### DTO 类
- `Agent-Studio/src/main/java/cn/ts/web/dto/agent/AgentConfigDTO.java`
- `Agent-Studio/src/main/java/cn/ts/web/dto/agent/ModelConfigDTO.java`
- `Agent-Studio/src/main/java/cn/ts/web/dto/agent/ToolDefinitionDTO.java`
- `Agent-Studio/src/main/java/cn/ts/web/dto/agent/CreateAgentDTO.java`
- `Agent-Studio/src/main/java/cn/ts/web/dto/agent/UpdateAgentDTO.java`

#### Mapper 接口
- `Agent-Studio/src/main/java/cn/ts/web/mapper/AgentConfigMapper.java`
- `Agent-Studio/src/main/java/cn/ts/web/mapper/ModelConfigMapper.java`
- `Agent-Studio/src/main/java/cn/ts/web/mapper/ToolDefinitionMapper.java`
- `Agent-Studio/src/main/java/cn/ts/web/mapper/AgentToolMappingMapper.java`

#### 服务层
- `Agent-Studio/src/main/java/cn/ts/web/service/AgentConfigService.java`
- `Agent-Studio/src/main/java/cn/ts/web/service/impl/AgentConfigServiceImpl.java`
- `Agent-Studio/src/main/java/cn/ts/web/service/ModelConfigService.java`
- `Agent-Studio/src/main/java/cn/ts/web/service/impl/ModelConfigServiceImpl.java`
- `Agent-Studio/src/main/java/cn/ts/web/service/ToolDefinitionService.java`
- `Agent-Studio/src/main/java/cn/ts/web/service/impl/ToolDefinitionServiceImpl.java`
- `Agent-Studio/src/main/java/cn/ts/web/service/ApiKeyEncryptionService.java`

#### Agent Factory（核心组装器）
- `agent-core/src/main/java/cn/ts/agent/factory/AgentFactory.java`

#### 自动扫描/同步组件
- `Agent-Studio/src/main/java/cn/ts/web/component/LocalToolScanner.java`
- `Agent-Studio/src/main/java/cn/ts/web/component/McpToolSyncService.java`

#### 控制器
- `Agent-Studio/src/main/java/cn/ts/web/controller/AgentManagementController.java`
- `Agent-Studio/src/main/java/cn/ts/web/controller/ModelManagementController.java`
- `Agent-Studio/src/main/java/cn/ts/web/controller/ToolManagementController.java`

#### 配置类
- `Agent-Studio/src/main/java/cn/ts/web/config/MyBatisConfig.java`
- `Agent-Studio/src/main/java/cn/ts/web/config/EncryptionConfig.java`

#### SQL 初始化脚本
- `Agent-Studio/src/main/resources/db/schema.sql`
- `Agent-Studio/src/main/resources/db/data.sql`

### 前端文件

#### 页面
- `frontend/src/views/agent/AgentManagement.vue`
- `frontend/src/views/agent/ToolMarket.vue`
- `frontend/src/views/agent/ModelManagement.vue`

#### 组件
- `frontend/src/components/agent/AgentForm.vue`
- `frontend/src/components/agent/AgentList.vue`
- `frontend/src/components/agent/ToolSelector.vue`
- `frontend/src/components/agent/PromptEditor.vue`

#### 状态管理
- `frontend/src/stores/agentConfig.ts`
- `frontend/src/stores/toolDefinition.ts`
- `frontend/src/stores/modelConfig.ts`

#### API 服务
- `frontend/src/api/agent.ts`
- `frontend/src/api/tool.ts`
- `frontend/src/api/model.ts`

#### 类型定义
- `frontend/src/types/agent.ts`
- `frontend/src/types/tool.ts`
- `frontend/src/types/model.ts`

---

## 实施步骤

### 阶段一：基础设施（第 1-2 周）

#### 1.1 数据库初始化
- [ ] 创建 `schema.sql` - 包含所有表结构
- [ ] 创建 `data.sql` - 初始化默认数据
- [ ] 配置 MyBatis（`application.yml`）
- [ ] 创建 `MyBatisConfig.java`

#### 1.2 实体类和 DTO
- [ ] 创建所有 Entity 类
- [ ] 创建所有 DTO 类
- [ ] 添加验证注解（@Valid、@NotNull 等）

#### 1.3 Mapper 接口
- [ ] 创建所有 Mapper 接口
- [ ] 使用 MyBatis 注解实现 CRUD
- [ ] 编写 Mapper 单元测试

### 阶段二：核心服务层（第 3-4 周）

#### 2.1 API 密钥加密服务
- [ ] 创建 `ApiKeyEncryptionService`
- [ ] 实现加密/解密方法（使用 AES-256）
- [ ] 配置加密密钥

#### 2.2 ModelConfigService
- [ ] 实现 ModelConfig CRUD
- [ ] 实现 ChatModel 动态创建逻辑
- [ ] 支持多提供商（OpenAI、Anthropic、ModelScope 等）
- [ ] 单元测试

#### 2.3 ToolDefinitionService
- [ ] 实现 ToolDefinition CRUD
- [ ] 实现工具实例化逻辑（本地 Bean + MCP 客户端）
- [ ] 单元测试

### 阶段三：自动化组件（第 5 周）

#### 3.1 本地工具自动扫描
- [ ] 创建 `LocalToolScanner`
- [ ] 扫描所有 @Component Bean 的 @Tool 注解方法
- [ ] 自动注册到 `tool_definition` 表
- [ ] 启动时执行扫描

#### 3.2 MCP 工具自动同步
- [ ] 创建 `McpToolSyncService`
- [ ] 监听 MCP 连接事件（建立/断开）
- [ ] 连接建立时自动同步工具到 `tool_definition` 表
- [ ] 断开时自动禁用相关工具

### 阶段四：Agent Factory（第 6 周）

#### 4.1 核心 AgentFactory
- [ ] 创建 `AgentFactory` 类
- [ ] 实现 `createAgent(AgentConfigDTO)` 方法
- [ ] 集成 ModelConfigService 创建 ChatModel
- [ ] 集成 ToolDefinitionService 实例化工具
- [ ] 组装 ReactAgent（支持流式/非流式）
- [ ] 单元测试

### 阶段五：Agent 配置服务（第 7 周）

#### 5.1 AgentConfigService
- [ ] 实现 AgentConfig CRUD
- [ ] 集成 AgentFactory 进行动态组装
- [ ] 实现 Agent 注册/注销逻辑
- [ ] **实现热重载**：配置变更时自动重新组装 Agent
- [ ] 与 AgentExecutionService 集成

#### 5.2 重构现有 AgentConfig
- [ ] 修改 `AgentConfig.java` 从数据库加载 Agent
- [ ] 启动时调用 `agentConfigService.registerAllActiveAgents()`
- [ ] 保持向后兼容

### 阶段六：REST API（第 8 周）

#### 6.1 AgentManagementController
- [ ] POST `/api/agents` - 创建 Agent
- [ ] PUT `/api/agents/{id}` - 更新 Agent（触发热重载）
- [ ] DELETE `/api/agents/{id}` - 删除 Agent
- [ ] GET `/api/agents` - 获取所有 Agent
- [ ] GET `/api/agents/{id}` - 获取单个 Agent
- [ ] POST `/api/agents/{id}/activate` - 激活 Agent
- [ ] POST `/api/agents/{id}/deactivate` - 停用 Agent
- [ ] POST `/api/agents/{id}/reload` - 手动重载 Agent
- [ ] POST `/api/agents/reload-all` - 重载所有 Agent

#### 6.2 ModelManagementController
- [ ] 完整的 Model CRUD API
- [ ] GET `/api/models/providers` - 获取提供商列表

#### 6.3 ToolManagementController
- [ ] 完整的 Tool CRUD API
- [ ] POST `/api/tools/scan-local` - 手动触发本地工具扫描
- [ ] POST `/api/tools/sync-mcp/{connectionName}` - 手动触发 MCP 工具同步

### 阶段七：前端开发（第 9-11 周）

#### 7.1 状态管理和 API
- [ ] 创建 `agentConfig` store
- [ ] 创建 `toolDefinition` store
- [ ] 创建 `modelConfig` store
- [ ] 实现 API 服务层

#### 7.2 Agent 配置页面
- [ ] Agent 列表展示
- [ ] 创建/编辑 Agent 表单
- [ ] 模型选择器
- [ ] 工具多选器（支持搜索和分类）
- [ ] Prompt 编辑器（支持语法高亮）
- [ ] Agent 操作按钮（激活/停用/重载/删除）

#### 7.3 工具市场页面
- [ ] 工具列表展示（本地/MCP 分类）
- [ ] 工具搜索和过滤
- [ ] 工具详情展示
- [ ] 工具启用/禁用

#### 7.4 模型配置页面
- [ ] 模型列表展示
- [ ] 添加/编辑模型表单
- [ ] 提供商管理
- [ ] API 密钥输入（显示为密码）

### 阶段八：集成和优化（第 12 周）

#### 8.1 热重载优化
- [ ] 监听数据库变更（或通过 API 触发）
- [ ] 优雅地重新组装 Agent（不影响正在执行的请求）
- [ ] 添加重载事件通知

#### 8.2 前端优化
- [ ] 添加加载状态和骨架屏
- [ ] 完善错误处理和提示
- [ ] 优化表单验证
- [ ] 添加操作确认对话框

#### 8.3 测试和文档
- [ ] 集成测试
- [ ] E2E 测试
- [ ] API 文档
- [ ] 用户使用手册

---

## 核心代码示例

### AgentFactory（核心组装器）

```java
package cn.ts.agent.factory;

import cn.ts.agent.ReactAgent;
import cn.ts.web.dto.agent.AgentConfigDTO;
import cn.ts.web.dto.agent.ToolDefinitionDTO;
import cn.ts.web.service.ModelConfigService;
import cn.ts.web.service.ToolDefinitionService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
public class AgentFactory {
    private final ModelConfigService modelConfigService;
    private final ToolDefinitionService toolDefinitionService;

    public AgentFactory(ModelConfigService modelConfigService,
                        ToolDefinitionService toolDefinitionService) {
        this.modelConfigService = modelConfigService;
        this.toolDefinitionService = toolDefinitionService;
    }

    /**
     * 根据配置动态创建 ReactAgent
     */
    public ReactAgent createAgent(AgentConfigDTO config) {
        // 1. 创建 ChatModel
        ChatModel chatModel = modelConfigService.createChatModel(config.getModelConfig());

        // 2. 准备工具列表
        List<ToolDefinitionDTO> toolDefs = config.getToolDefinitions();
        Object[] tools = toolDefinitionService.instantiateTools(toolDefs);

        // 3. 构建 ReactAgent
        return new ReactAgent(
            config.getAgentName(),
            config.getDescription(),
            chatModel,
            config.isEnableStreaming(),
            tools
        );
    }
}
```

### 热重载实现

```java
package cn.ts.web.service.impl;

import cn.ts.agent.ReactAgent;
import cn.ts.agent.factory.AgentFactory;
import cn.ts.web.dto.agent.AgentConfigDTO;
import cn.ts.web.mapper.AgentConfigMapper;
import cn.ts.web.service.AgentConfigService;
import cn.ts.web.service.AgentExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentConfigServiceImpl implements AgentConfigService {

    private final AgentFactory agentFactory;
    private final AgentExecutionService agentExecutionService;
    private final AgentConfigMapper mapper;
    private final Map<String, ReactAgent> agentRegistry = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public AgentConfigDTO updateAgent(Long id, AgentConfigDTO dto) {
        // 1. 更新数据库
        mapper.updateById(convertToEntity(dto));

        // 2. 重新加载配置
        AgentConfigDTO updated = getById(id);

        // 3. 热重载 Agent
        hotReloadAgent(updated.getAgentName());

        return updated;
    }

    /**
     * 热重载 Agent（不影响正在执行的请求）
     */
    private void hotReloadAgent(String agentName) {
        // 1. 从数据库获取最新配置
        AgentConfigDTO config = getAgentByName(agentName);

        // 2. 重新组装 Agent
        ReactAgent newAgent = agentFactory.createAgent(config);

        // 3. 替换注册表中的 Agent
        ReactAgent oldAgent = agentRegistry.get(agentName);
        if (oldAgent != null) {
            agentExecutionService.unregisterGraph(agentName);
        }
        agentExecutionService.registerGraph(agentName, newAgent.getGraph());
        agentRegistry.put(agentName, newAgent);
    }
}
```

### 本地工具自动扫描

```java
package cn.ts.web.component;

import cn.ts.web.dto.agent.ToolDefinitionDTO;
import cn.ts.web.service.ToolDefinitionService;
import org.springframework.ai.model.function.Tool;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Map;

@Component
public class LocalToolScanner implements ApplicationListener<ApplicationReadyEvent> {

    private final ApplicationContext applicationContext;
    private final ToolDefinitionService toolDefinitionService;

    public LocalToolScanner(ApplicationContext applicationContext,
                           ToolDefinitionService toolDefinitionService) {
        this.applicationContext = applicationContext;
        this.toolDefinitionService = toolDefinitionService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        scanAndRegisterTools();
    }

    /**
     * 扫描所有带 @Tool 注解的方法并注册到工具市场
     */
    public void scanAndRegisterTools() {
        // 获取所有 Spring Bean
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Component.class);

        for (Object bean : beans.values()) {
            Class<?> beanClass = bean.getClass();
            Method[] methods = beanClass.getDeclaredMethods();

            for (Method method : methods) {
                if (method.isAnnotationPresent(Tool.class)) {
                    Tool tool = method.getAnnotation(Tool.class);

                    ToolDefinitionDTO dto = new ToolDefinitionDTO();
                    dto.setToolName(tool.name());
                    dto.setDisplayName(tool.name());
                    dto.setDescription(tool.description());
                    dto.setToolType(ToolType.LOCAL);
                    dto.setClassName(beanClass.getName());
                    dto.setIsActive(true);

                    // 创建或更新工具定义
                    toolDefinitionService.createOrUpdateTool(dto);
                }
            }
        }
    }
}
```

### MCP 工具自动同步

```java
package cn.ts.web.component;

import cn.ts.web.dto.agent.ToolDefinitionDTO;
import cn.ts.web.service.ToolDefinitionService;
import org.springframework.ai.mcp.sync.McpSyncClient;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import cn.ts.agent.mcp.McpManager;
import cn.ts.agent.mcp.event.McpConnectionEstablishedEvent;
import cn.ts.agent.mcp.event.McpConnectionLostEvent;

@Service
public class McpToolSyncService {

    private final McpManager mcpManager;
    private final ToolDefinitionService toolDefinitionService;

    public McpToolSyncService(McpManager mcpManager,
                             ToolDefinitionService toolDefinitionService) {
        this.mcpManager = mcpManager;
        this.toolDefinitionService = toolDefinitionService;
    }

    /**
     * MCP 连接建立时自动同步工具
     */
    @EventListener
    public void onMcpConnectionEstablished(McpConnectionEstablishedEvent event) {
        String connectionName = event.getConnectionName();

        // 获取 MCP 客户端
        McpSyncClient client = mcpManager.getConnection(connectionName)
            .orElseThrow(() -> new IllegalStateException("MCP connection not found: " + connectionName))
            .getClient();

        // 同步工具到工具市场
        client.listTools().tools().forEach(mcpTool -> {
            ToolDefinitionDTO dto = new ToolDefinitionDTO();
            dto.setToolName(connectionName + ":" + mcpTool.name());
            dto.setDisplayName(mcpTool.name());
            dto.setDescription(mcpTool.description());
            dto.setToolType(ToolType.MCP);
            dto.setMcpConnectionName(connectionName);
            dto.setMcpToolName(mcpTool.name());
            dto.setIsActive(true);

            toolDefinitionService.createOrUpdateTool(dto);
        });
    }

    /**
     * MCP 连接断开时自动禁用工具
     */
    @EventListener
    public void onMcpConnectionLost(McpConnectionLostEvent event) {
        String connectionName = event.getConnectionName();

        // 禁用该连接下的所有工具
        toolDefinitionService.disableToolsByConnection(connectionName);
    }
}
```

### ModelConfigService（ChatModel 动态创建）

```java
package cn.ts.web.service.impl;

import cn.ts.web.dto.agent.ModelConfigDTO;
import cn.ts.web.service.ApiKeyEncryptionService;
import cn.ts.web.service.ModelConfigService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

@Service
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ApiKeyEncryptionService encryptionService;

    @Override
    public ChatModel createChatModel(ModelConfigDTO config) {
        // 解密 API 密钥
        String apiKey = encryptionService.decrypt(config.getApiKey());

        return switch (config.getProvider().toLowerCase()) {
            case "openai" -> createOpenAIChatModel(config, apiKey);
            case "anthropic" -> createAnthropicChatModel(config, apiKey);
            case "modelscope" -> createModelScopeChatModel(config, apiKey);
            default -> throw new IllegalArgumentException("Unknown provider: " + config.getProvider());
        };
    }

    private ChatModel createOpenAIChatModel(ModelConfigDTO config, String apiKey) {
        OpenAiApi api = OpenAiApi.builder()
            .baseUrl(config.getBaseUrl())
            .apiKey(apiKey)
            .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(config.getModelId())
            .build();

        return new OpenAiChatModel(api, options);
    }

    private ChatModel createAnthropicChatModel(ModelConfigDTO config, String apiKey) {
        AnthropicApi api = new AnthropicApi(apiKey, config.getBaseUrl());
        return new AnthropicChatModel(api);
    }
}
```

---

## API 端点设计

### Agent 管理 API

```
POST   /api/agents                      创建 Agent
PUT    /api/agents/{id}                 更新 Agent（触发热重载）
DELETE /api/agents/{id}                 删除 Agent
GET    /api/agents                      获取所有 Agent
GET    /api/agents/{id}                 获取单个 Agent
POST   /api/agents/{id}/activate        激活 Agent
POST   /api/agents/{id}/deactivate      停用 Agent
POST   /api/agents/{id}/reload          手动重载 Agent
POST   /api/agents/reload-all           重载所有 Agent
```

### 模型管理 API

```
POST   /api/models                      创建模型配置
PUT    /api/models/{id}                 更新模型配置
DELETE /api/models/{id}                 删除模型配置
GET    /api/models                      获取所有模型配置
GET    /api/models/{id}                 获取单个模型配置
GET    /api/models/providers            获取支持的提供商列表
```

### 工具管理 API

```
POST   /api/tools                       创建工具定义
PUT    /api/tools/{id}                  更新工具定义
DELETE /api/tools/{id}                  删除工具定义
GET    /api/tools                       获取所有工具定义
GET    /api/tools/{id}                  获取单个工具定义
GET    /api/tools?type=LOCAL            按类型过滤工具
POST   /api/tools/scan-local            手动触发本地工具扫描
POST   /api/tools/sync-mcp/{name}       手动触发 MCP 工具同步
```

---

## 验证步骤

### 1. 后端验证

```bash
# 1. 启动应用，检查数据库初始化
cd Agent-Studio
mvn spring-boot:run

# 2. 检查本地工具是否自动扫描
curl http://localhost:8080/api/tools | jq '.[] | select(.toolType=="LOCAL")'

# 3. 检查 MCP 工具是否自动同步
curl http://localhost:8080/api/tools | jq '.[] | select(.toolType=="MCP")'

# 4. 创建模型配置
curl -X POST http://localhost:8080/api/models \
  -H "Content-Type: application/json" \
  -d '{
    "modelName": "gpt-4",
    "displayName": "GPT-4",
    "provider": "openai",
    "modelId": "gpt-4-turbo",
    "baseUrl": "https://api.openai.com",
    "apiKey": "sk-xxx"
  }'

# 5. 创建 Agent 配置
curl -X POST http://localhost:8080/api/agents \
  -H "Content-Type: application/json" \
  -d '{
    "agentName": "MyAgent",
    "displayName": "我的助手",
    "description": "自定义助手",
    "modelId": 1,
    "systemPrompt": "你是一个有用的助手",
    "toolIds": [1, 2, 3]
  }'

# 6. 验证 Agent 已注册
curl http://localhost:8080/api/stream/agents

# 7. 执行 Agent
curl "http://localhost:8080/api/stream/agent/MyAgent/execute?message=你好"
```

### 2. 前端验证

```bash
# 1. 启动前端
cd frontend
npm run dev

# 2. 打开浏览器访问
# http://localhost:5173

# 3. 测试流程
# - 导航到模型配置页面，创建模型配置
# - 导航到工具市场，查看自动扫描的本地工具和 MCP 工具
# - 导航到 Agent 管理，创建新的 Agent
#   * 选择模型
#   * 输入系统 Prompt
#   * 选择需要的工具
# - 保存 Agent，立即执行（验证热重载）
# - 修改 Agent 配置，再次执行（验证更新生效）
```

---

## 注意事项

### 1. API 密钥安全
- 使用 AES-256 加密存储 API 密钥
- 加密密钥通过环境变量配置
- 前端传输时使用 HTTPS

### 2. 向后兼容
- 保持现有 AgentConfig 的工作方式
- 平滑迁移，支持双模式运行（硬编码 + 数据库）

### 3. 热重载安全
- 重载时不影响正在执行的请求
- 使用 Copy-on-Write 模式替换 Agent
- 添加重载事件通知

### 4. 工具实例化
- 本地工具从 Spring 容器获取 Bean
- MCP 工具从 McpManager 获取客户端
- 确保工具可序列化（支持多线程）

### 5. 事务管理
- Agent 创建时使用 @Transactional 保证一致性
- 工具关联失败时回滚整个 Agent 创建

### 6. 性能优化
- Agent 缓存（避免重复创建）
- 工具实例池化
- 数据库查询优化（关联查询、索引）

---

## 扩展性考虑

### 未来可能的扩展

1. **多租户支持**
   - 添加 tenant_id 字段
   - 实现租户隔离

2. **Agent 版本管理**
   - 添加 version 字段
   - 支持 Agent 版本回滚

3. **Agent 模板**
   - 创建 Agent 模板表
   - 支持基于模板快速创建 Agent

4. **Agent 权限控制**
   - 添加角色和权限管理
   - 控制 Agent 访问权限

5. **Agent 性能监控**
   - 记录 Agent 执行次数
   - 统计工具使用频率
   - 性能指标展示

6. **Agent 分享和导出**
   - 支持 Agent 配置导出为 JSON
   - 支持 Agent 分享给其他用户

---

## 总结

本实施计划提供了一个完整的 Agent 配置和组装系统设计，核心特性包括：

1. **动态组装** - 基于 AgentFactory 动态创建 ReactAgent
2. **工具市场** - 统一管理本地工具和 MCP 工具
3. **模型市场** - 支持多提供商模型配置
4. **热重载** - 配置变更后立即生效，无需重启
5. **自动化** - 本地工具自动扫描，MCP 工具自动同步

整个系统设计遵循了 Spring Boot 和 Spring AI 的最佳实践，与现有架构无缝集成。
