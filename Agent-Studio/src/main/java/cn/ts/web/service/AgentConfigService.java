package cn.ts.web.service;

import cn.ts.agent.core.ReactAgent;
import cn.ts.web.dto.agent.AgentConfigDTO;

import java.util.List;

/**
 * Agent 配置服务接口
 */
public interface AgentConfigService {

    /**
     * 创建 Agent 配置
     *
     * @param dto Agent 配置 DTO
     * @return 创建后的 Agent 配置
     */
    AgentConfigDTO createAgent(AgentConfigDTO dto);

    /**
     * 更新 Agent 配置
     *
     * @param id  Agent 配置 ID
     * @param dto Agent 配置 DTO
     * @return 更新后的 Agent 配置
     */
    AgentConfigDTO updateAgent(Long id, AgentConfigDTO dto);

    /**
     * 删除 Agent 配置
     *
     * @param id Agent 配置 ID
     */
    void deleteAgent(Long id);

    /**
     * 根据ID获取 Agent 配置
     *
     * @param id Agent 配置 ID
     * @return Agent 配置
     */
    AgentConfigDTO getAgentById(Long id);

    /**
     * 根据 Agent 名称获取 Agent 配置
     *
     * @param agentName Agent 名称
     * @return Agent 配置
     */
    AgentConfigDTO getAgentByName(String agentName);

    /**
     * 获取所有 Agent 配置
     *
     * @return Agent 配置列表
     */
    List<AgentConfigDTO> getAllAgents();

    /**
     * 获取激活的 Agent 配置
     *
     * @return Agent 配置列表
     */
    List<AgentConfigDTO> getActiveAgents();

    /**
     * 组装 Agent
     *
     * @param config Agent 配置
     * @return ReactAgent 实例
     */
    ReactAgent assembleAgent(AgentConfigDTO config);

    /**
     * 注册 Agent 到执行服务
     *
     * @param agentName Agent 名称
     * @param agent     ReactAgent 实例
     */
    void registerAgentToExecutionService(String agentName, ReactAgent agent);

    /**
     * 从执行服务注销 Agent
     *
     * @param agentName Agent 名称
     */
    void unregisterAgentFromExecutionService(String agentName);

    /**
     * 注册所有激活的 Agent
     */
    void registerAllActiveAgents();

    /**
     * 激活 Agent
     *
     * @param id Agent 配置 ID
     */
    void activateAgent(Long id);

    /**
     * 停用 Agent
     *
     * @param id Agent 配置 ID
     */
    void deactivateAgent(Long id);

    /**
     * 重载 Agent（热重载）
     *
     * @param agentName Agent 名称
     */
    void reloadAgent(String agentName);

    /**
     * 重载所有 Agent
     */
    void reloadAllAgents();
}
