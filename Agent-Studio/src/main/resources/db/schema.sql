-- =============================================
-- Agent 配置和组装系统 - 数据库表结构
-- PostgreSQL 版本
-- =============================================
DROP TABLE IF EXISTS agent_tool_mapping CASCADE;
DROP TABLE IF EXISTS agent_config CASCADE;
DROP TABLE IF EXISTS mcp_connection_config CASCADE;
DROP TABLE IF EXISTS tool_definition CASCADE;
DROP TABLE IF EXISTS model_config CASCADE;

DROP TYPE IF EXISTS connection_type_enum CASCADE;
DROP TYPE IF EXISTS tool_type_enum CASCADE;
-- 1. 模型配置表（API 密钥加密存储）
CREATE TABLE IF NOT EXISTS model_config (
    id BIGSERIAL PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model_id VARCHAR(100) NOT NULL,
    base_url VARCHAR(500),
    api_key_encrypted VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_model_config_provider ON model_config(provider);
CREATE INDEX IF NOT EXISTS idx_model_config_is_active ON model_config(is_active);
CREATE INDEX IF NOT EXISTS idx_model_config_model_name ON model_config(model_name);

-- 添加注释
COMMENT ON TABLE model_config IS '模型配置表';
COMMENT ON COLUMN model_config.id IS '主键ID';
COMMENT ON COLUMN model_config.model_name IS '模型名称（唯一标识）';
COMMENT ON COLUMN model_config.display_name IS '显示名称';
COMMENT ON COLUMN model_config.provider IS '提供商（openai/anthropic/modelscope等）';
COMMENT ON COLUMN model_config.model_id IS '模型ID（如gpt-4-turbo）';
COMMENT ON COLUMN model_config.base_url IS 'API基础URL';
COMMENT ON COLUMN model_config.api_key_encrypted IS '加密后的API密钥';
COMMENT ON COLUMN model_config.is_active IS '是否可用';
COMMENT ON COLUMN model_config.created_at IS '创建时间';
COMMENT ON COLUMN model_config.updated_at IS '更新时间';

-- 2. 工具定义表
CREATE TABLE IF NOT EXISTS tool_definition (
    id BIGSERIAL PRIMARY KEY,
    tool_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,
    tool_type VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    class_name VARCHAR(200),
    mcp_connection_name VARCHAR(100),
    mcp_tool_name VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_tool_definition_tool_type ON tool_definition(tool_type);
CREATE INDEX IF NOT EXISTS idx_tool_definition_is_active ON tool_definition(is_active);
CREATE INDEX IF NOT EXISTS idx_tool_definition_mcp_connection ON tool_definition(mcp_connection_name);
CREATE INDEX IF NOT EXISTS idx_tool_definition_tool_name ON tool_definition(tool_name);

-- 添加注释
COMMENT ON TABLE tool_definition IS '工具定义表';
COMMENT ON COLUMN tool_definition.id IS '主键ID';
COMMENT ON COLUMN tool_definition.tool_name IS '工具名称（唯一标识）';
COMMENT ON COLUMN tool_definition.display_name IS '显示名称';
COMMENT ON COLUMN tool_definition.description IS '工具描述';
COMMENT ON COLUMN tool_definition.tool_type IS '工具类型';
COMMENT ON COLUMN tool_definition.class_name IS 'Java类名（本地工具）';
COMMENT ON COLUMN tool_definition.mcp_connection_name IS 'MCP连接名称（MCP工具）';
COMMENT ON COLUMN tool_definition.mcp_tool_name IS 'MCP工具名称';
COMMENT ON COLUMN tool_definition.is_active IS '是否可用';
COMMENT ON COLUMN tool_definition.created_at IS '创建时间';
COMMENT ON COLUMN tool_definition.updated_at IS '更新时间';

-- 3. MCP 连接配置表（持久化现有配置）
CREATE TABLE IF NOT EXISTS mcp_connection_config (
    id BIGSERIAL PRIMARY KEY,
    connection_name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    connection_type VARCHAR(20) NOT NULL,
    command VARCHAR(500),
    args JSONB,
    env JSONB,
    url VARCHAR(500),
    timeout_seconds INT DEFAULT 30,
    auto_reconnect BOOLEAN DEFAULT TRUE,
    max_retries INT DEFAULT 3,
    retry_interval_seconds INT DEFAULT 5,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_mcp_connection_config_connection_type ON mcp_connection_config(connection_type);
CREATE INDEX IF NOT EXISTS idx_mcp_connection_config_is_active ON mcp_connection_config(is_active);
CREATE INDEX IF NOT EXISTS idx_mcp_connection_config_connection_name ON mcp_connection_config(connection_name);

-- 添加注释
COMMENT ON TABLE mcp_connection_config IS 'MCP连接配置表';
COMMENT ON COLUMN mcp_connection_config.id IS '主键ID';
COMMENT ON COLUMN mcp_connection_config.connection_name IS '连接名称（唯一标识）';
COMMENT ON COLUMN mcp_connection_config.description IS '连接描述';
COMMENT ON COLUMN mcp_connection_config.connection_type IS '连接类型';
COMMENT ON COLUMN mcp_connection_config.command IS '命令（STDIO类型）';
COMMENT ON COLUMN mcp_connection_config.args IS '参数列表（STDIO类型）';
COMMENT ON COLUMN mcp_connection_config.env IS '环境变量（STDIO类型）';
COMMENT ON COLUMN mcp_connection_config.url IS 'URL（SSE类型）';
COMMENT ON COLUMN mcp_connection_config.timeout_seconds IS '超时时间（秒）';
COMMENT ON COLUMN mcp_connection_config.auto_reconnect IS '是否自动重连';
COMMENT ON COLUMN mcp_connection_config.max_retries IS '最大重试次数';
COMMENT ON COLUMN mcp_connection_config.retry_interval_seconds IS '重试间隔（秒）';
COMMENT ON COLUMN mcp_connection_config.is_active IS '是否激活';
COMMENT ON COLUMN mcp_connection_config.created_at IS '创建时间';
COMMENT ON COLUMN mcp_connection_config.updated_at IS '更新时间';

-- 4. Agent 配置表
CREATE TABLE IF NOT EXISTS agent_config (
    id BIGSERIAL PRIMARY KEY,
    agent_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,
    model_id BIGINT NOT NULL,
    system_prompt TEXT,
    max_iterations INT DEFAULT 10,
    temperature NUMERIC(3,2) DEFAULT 0.70,
    enable_streaming BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(50) DEFAULT 'system',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_config_model FOREIGN KEY (model_id) REFERENCES model_config(id) ON DELETE RESTRICT
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_agent_config_agent_name ON agent_config(agent_name);
CREATE INDEX IF NOT EXISTS idx_agent_config_is_active ON agent_config(is_active);
CREATE INDEX IF NOT EXISTS idx_agent_config_model_id ON agent_config(model_id);

-- 添加注释
COMMENT ON TABLE agent_config IS 'Agent配置表';
COMMENT ON COLUMN agent_config.id IS '主键ID';
COMMENT ON COLUMN agent_config.agent_name IS 'Agent名称（唯一标识）';
COMMENT ON COLUMN agent_config.display_name IS '显示名称';
COMMENT ON COLUMN agent_config.description IS 'Agent描述';
COMMENT ON COLUMN agent_config.model_id IS '关联模型ID';
COMMENT ON COLUMN agent_config.system_prompt IS '系统提示词';
COMMENT ON COLUMN agent_config.max_iterations IS '最大迭代次数';
COMMENT ON COLUMN agent_config.temperature IS '温度参数';
COMMENT ON COLUMN agent_config.enable_streaming IS '是否启用流式输出';
COMMENT ON COLUMN agent_config.is_active IS '是否激活';
COMMENT ON COLUMN agent_config.created_by IS '创建者';
COMMENT ON COLUMN agent_config.created_at IS '创建时间';
COMMENT ON COLUMN agent_config.updated_at IS '更新时间';

-- 5. Agent-工具关联表
CREATE TABLE IF NOT EXISTS agent_tool_mapping (
    id BIGSERIAL PRIMARY KEY,
    agent_config_id BIGINT NOT NULL,
    tool_definition_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (agent_config_id, tool_definition_id),
    CONSTRAINT fk_agent_tool_mapping_agent FOREIGN KEY (agent_config_id) REFERENCES agent_config(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_tool_mapping_tool FOREIGN KEY (tool_definition_id) REFERENCES tool_definition(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_agent_tool_mapping_agent_config ON agent_tool_mapping(agent_config_id);
CREATE INDEX IF NOT EXISTS idx_agent_tool_mapping_tool_definition ON agent_tool_mapping(tool_definition_id);

-- 添加注释
COMMENT ON TABLE agent_tool_mapping IS 'Agent工具关联表';
COMMENT ON COLUMN agent_tool_mapping.id IS '主键ID';
COMMENT ON COLUMN agent_tool_mapping.agent_config_id IS 'Agent配置ID';
COMMENT ON COLUMN agent_tool_mapping.tool_definition_id IS '工具定义ID';
COMMENT ON COLUMN agent_tool_mapping.created_at IS '创建时间';

-- 创建自动更新 updated_at 的触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为需要的表创建触发器
DROP TRIGGER IF EXISTS update_model_config_updated_at ON model_config;
CREATE TRIGGER update_model_config_updated_at
    BEFORE UPDATE ON model_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_tool_definition_updated_at ON tool_definition;
CREATE TRIGGER update_tool_definition_updated_at
    BEFORE UPDATE ON tool_definition
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_mcp_connection_config_updated_at ON mcp_connection_config;
CREATE TRIGGER update_mcp_connection_config_updated_at
    BEFORE UPDATE ON mcp_connection_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_agent_config_updated_at ON agent_config;
CREATE TRIGGER update_agent_config_updated_at
    BEFORE UPDATE ON agent_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
