-- =============================================
-- Agent 配置和组装系统 - 初始化数据
-- PostgreSQL 版本
-- =============================================

-- 注意：API 密钥需要先加密后插入，这里提供示例结构
-- 实际使用时需要通过 ApiKeyEncryptionService 加密

-- 1. 插入示例模型配置
-- 密钥为占位符，实际使用时需要替换为真实密钥并加密
INSERT INTO model_config (model_name, display_name, provider, model_id, base_url, api_key_encrypted, is_active)
VALUES
    ('gpt-4-turbo', 'GPT-4 Turbo', 'openai', 'gpt-4-turbo', 'https://api.openai.com', 'ENCRYPTED_KEY_PLACEHOLDER', FALSE),
    ('gpt-3.5-turbo', 'GPT-3.5 Turbo', 'openai', 'gpt-3.5-turbo', 'https://api.openai.com', 'ENCRYPTED_KEY_PLACEHOLDER', FALSE),
    ('claude-3-opus', 'Claude 3 Opus', 'anthropic', 'claude-3-opus-20240229', 'https://api.anthropic.com', 'ENCRYPTED_KEY_PLACEHOLDER', FALSE)
ON CONFLICT (model_name) DO UPDATE SET updated_at = CURRENT_TIMESTAMP;

-- 2. 插入示例本地工具定义
-- 这些将由 LocalToolScanner 自动扫描并注册，这里仅作为示例
-- INSERT INTO tool_definition (tool_name, display_name, description, tool_type, class_name, is_active)
-- VALUES
--     ('currentTime', '当前时间', '获取当前系统时间', 'LOCAL', 'cn.ts.agent.example.SimpleTools', TRUE),
--     ('calculator', '计算器', '执行数学计算', 'LOCAL', 'cn.ts.agent.example.SimpleTools', TRUE)
-- ON CONFLICT (tool_name) DO UPDATE SET updated_at = CURRENT_TIMESTAMP;

-- 3. 插入示例 MCP 连接配置
-- INSERT INTO mcp_connection_config (connection_name, description, connection_type, command, args, env, is_active)
-- VALUES
--     ('amap', '高德地图 MCP 服务', 'STDIO', 'npx',
--      '["-y", "@amap/amap-maps-mcp-server"]'::jsonb,
--      '{"AMAP_MAPS_API_KEY": "your_api_key"}'::jsonb,
--      FALSE)
-- ON CONFLICT (connection_name) DO UPDATE SET updated_at = CURRENT_TIMESTAMP;

-- 4. 插入示例 Agent 配置
-- 注意：需要先有 model_config 和 tool_definition 的数据
-- INSERT INTO agent_config (agent_name, display_name, description, model_id, system_prompt, max_iterations, temperature, enable_streaming, is_active, created_by)
-- VALUES
--     ('ExampleAgent', '示例助手', '这是一个示例 Agent 配置',
--      (SELECT id FROM model_config WHERE model_name = 'gpt-4-turbo' LIMIT 1),
--      '你是一个有用的助手，可以帮助用户解答问题。',
--      10, 0.70, TRUE, TRUE, 'system')
-- ON CONFLICT (agent_name) DO UPDATE SET updated_at = CURRENT_TIMESTAMP;

-- 5. 插入 Agent-工具关联
-- INSERT INTO agent_tool_mapping (agent_config_id, tool_definition_id)
-- SELECT
--     (SELECT id FROM agent_config WHERE agent_name = 'ExampleAgent' LIMIT 1),
--     id
-- FROM tool_definition
-- WHERE tool_name IN ('currentTime', 'calculator')
-- ON CONFLICT (agent_config_id, tool_definition_id) DO NOTHING;
