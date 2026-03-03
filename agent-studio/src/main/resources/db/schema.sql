-- =============================================
-- Agent 配置和组装系统 - 数据库表结构
-- PostgreSQL 版本
-- =============================================
DROP TABLE IF EXISTS agent_tool_mapping CASCADE;
DROP TABLE IF EXISTS agent_subagent_mapping CASCADE;
DROP TABLE IF EXISTS agent_config CASCADE;
DROP TABLE IF EXISTS mcp_connection_config CASCADE;
DROP TABLE IF EXISTS tool_definition CASCADE;
DROP TABLE IF EXISTS model_config CASCADE;
DROP TABLE IF EXISTS llm_prompt_audit CASCADE;
DROP TABLE IF EXISTS channel_config CASCADE;
DROP TABLE IF EXISTS cron_job_run CASCADE;
DROP TABLE IF EXISTS cron_job CASCADE;

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
    max_iterations INT DEFAULT 100,
    temperature NUMERIC(3,2) DEFAULT 0.70,
    enable_streaming BOOLEAN DEFAULT TRUE,
    enable_subagent_interceptor BOOLEAN DEFAULT FALSE,
    include_general_purpose BOOLEAN DEFAULT TRUE,
    subagent_tools_policy VARCHAR(20) DEFAULT 'INHERIT',
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
COMMENT ON COLUMN agent_config.enable_subagent_interceptor IS '是否启用SubAgentInterceptor';
COMMENT ON COLUMN agent_config.include_general_purpose IS '是否包含默认general-purpose子代理';
COMMENT ON COLUMN agent_config.subagent_tools_policy IS '子代理工具策略(INHERIT/CUSTOM)';
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

-- 6. Agent-SubAgent 关联表
CREATE TABLE IF NOT EXISTS agent_subagent_mapping (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    subagent_type VARCHAR(100) NOT NULL,
    target_agent_id BIGINT NOT NULL,
    description TEXT,
    tools_policy VARCHAR(20) DEFAULT 'INHERIT',
    custom_tool_ids JSONB,
    sort_order INT DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_agent_subagent_type UNIQUE (agent_id, subagent_type),
    CONSTRAINT fk_agent_subagent_agent FOREIGN KEY (agent_id) REFERENCES agent_config(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_subagent_target FOREIGN KEY (target_agent_id) REFERENCES agent_config(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_agent_subagent_agent ON agent_subagent_mapping(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_subagent_target ON agent_subagent_mapping(target_agent_id);

COMMENT ON TABLE agent_subagent_mapping IS 'Agent子代理映射表';
COMMENT ON COLUMN agent_subagent_mapping.agent_id IS '主代理ID';
COMMENT ON COLUMN agent_subagent_mapping.subagent_type IS '子代理类型标识';
COMMENT ON COLUMN agent_subagent_mapping.target_agent_id IS '目标子代理ID';
COMMENT ON COLUMN agent_subagent_mapping.tools_policy IS '工具策略(可覆盖主配置)';
COMMENT ON COLUMN agent_subagent_mapping.custom_tool_ids IS 'CUSTOM模式下指定工具ID列表';
COMMENT ON COLUMN agent_subagent_mapping.sort_order IS '排序';
COMMENT ON COLUMN agent_subagent_mapping.enabled IS '是否启用';

-- 7. LLM Prompt 审计日志
CREATE TABLE IF NOT EXISTS llm_prompt_audit (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(255),
    execution_id VARCHAR(255),
    agent_name VARCHAR(100),
    phase VARCHAR(20) NOT NULL,
    request_json JSONB,
    response_json JSONB,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_llm_prompt_audit_trace_id ON llm_prompt_audit(trace_id);
CREATE INDEX IF NOT EXISTS idx_llm_prompt_audit_session_id ON llm_prompt_audit(session_id);
CREATE INDEX IF NOT EXISTS idx_llm_prompt_audit_created_at ON llm_prompt_audit(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_llm_prompt_audit_agent_name ON llm_prompt_audit(agent_name);

COMMENT ON TABLE llm_prompt_audit IS 'LLM 调用业务层审计日志';
COMMENT ON COLUMN llm_prompt_audit.trace_id IS '单次模型调用链路ID';
COMMENT ON COLUMN llm_prompt_audit.session_id IS '会话ID';
COMMENT ON COLUMN llm_prompt_audit.execution_id IS '执行ID';
COMMENT ON COLUMN llm_prompt_audit.agent_name IS 'Agent 名称';
COMMENT ON COLUMN llm_prompt_audit.phase IS '阶段: REQUEST/RESPONSE/ERROR';
COMMENT ON COLUMN llm_prompt_audit.request_json IS '请求侧审计数据';
COMMENT ON COLUMN llm_prompt_audit.response_json IS '响应侧审计数据';
COMMENT ON COLUMN llm_prompt_audit.error_message IS '错误信息';
COMMENT ON COLUMN llm_prompt_audit.created_at IS '创建时间';

-- 8. Channel 配置表
CREATE TABLE IF NOT EXISTS channel_config (
    id BIGSERIAL PRIMARY KEY,
    channel_name VARCHAR(100) NOT NULL UNIQUE,
    channel_type VARCHAR(50) NOT NULL,
    config_json JSONB,
    enabled BOOLEAN DEFAULT TRUE,
    status VARCHAR(20) DEFAULT 'stopped',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_channel_config_type ON channel_config(channel_type);
CREATE INDEX IF NOT EXISTS idx_channel_config_enabled ON channel_config(enabled);

COMMENT ON TABLE channel_config IS '渠道接入配置';
COMMENT ON COLUMN channel_config.channel_name IS '渠道配置唯一名称';
COMMENT ON COLUMN channel_config.channel_type IS '渠道类型';
COMMENT ON COLUMN channel_config.config_json IS '渠道配置JSON';
COMMENT ON COLUMN channel_config.enabled IS '是否启用';
COMMENT ON COLUMN channel_config.status IS '运行状态';

-- 9. Cron 作业表
CREATE TABLE IF NOT EXISTS cron_job (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(120) NOT NULL UNIQUE,
    cron_expression VARCHAR(120) NOT NULL,
    zone_id VARCHAR(64) DEFAULT 'Asia/Shanghai',
    agent_name VARCHAR(100) NOT NULL,
    session_id VARCHAR(255),
    input_text TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    max_retry_count INT DEFAULT 1,
    retry_interval_seconds INT DEFAULT 10,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    last_status VARCHAR(20),
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cron_job_enabled ON cron_job(enabled);
CREATE INDEX IF NOT EXISTS idx_cron_job_next_run ON cron_job(next_run_at);

COMMENT ON TABLE cron_job IS 'cron任务配置';
COMMENT ON COLUMN cron_job.job_name IS '任务名';
COMMENT ON COLUMN cron_job.cron_expression IS 'cron表达式';
COMMENT ON COLUMN cron_job.zone_id IS '时区';
COMMENT ON COLUMN cron_job.agent_name IS '执行目标agent';
COMMENT ON COLUMN cron_job.session_id IS '目标会话id';
COMMENT ON COLUMN cron_job.input_text IS '任务输入';
COMMENT ON COLUMN cron_job.last_status IS '最近一次状态';

-- 10. Cron 作业运行记录
CREATE TABLE IF NOT EXISTS cron_job_run (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    execution_id VARCHAR(255),
    error_message TEXT,
    CONSTRAINT fk_cron_job_run_job FOREIGN KEY (job_id) REFERENCES cron_job(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cron_job_run_job_id ON cron_job_run(job_id);
CREATE INDEX IF NOT EXISTS idx_cron_job_run_started_at ON cron_job_run(started_at DESC);

COMMENT ON TABLE cron_job_run IS 'cron任务运行记录';
COMMENT ON COLUMN cron_job_run.trigger_type IS '触发类型:auto/manual';
COMMENT ON COLUMN cron_job_run.status IS '运行状态';

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

DROP TRIGGER IF EXISTS update_channel_config_updated_at ON channel_config;
CREATE TRIGGER update_channel_config_updated_at
    BEFORE UPDATE ON channel_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_cron_job_updated_at ON cron_job;
CREATE TRIGGER update_cron_job_updated_at
    BEFORE UPDATE ON cron_job
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
