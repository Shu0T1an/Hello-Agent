package cn.ts.agent.mcp;

import cn.ts.agent.mcp.model.McpConnection;
import cn.ts.agent.mcp.model.McpConnectionConfig;
import cn.ts.agent.mcp.model.McpStatistics;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Optional;

/**
 * MCP 管理器接口
 * <p>
 * 负责 MCP 连接的注册、删除、查询、健康检查和自动重连功能
 * </p>
 *
 * @author tianshuo
 */
public interface McpManager {

    /**
     * 注册一个新的 MCP 连接
     *
     * @param config MCP 连接配置
     * @return 是否注册成功
     * @throws IllegalArgumentException 如果连接名称已存在
     */
    boolean registerConnection(McpConnectionConfig config);

    /**
     * 注销指定的 MCP 连接
     *
     * @param name 连接名称
     * @throws IllegalArgumentException 如果连接不存在
     */
    void unregisterConnection(String name);

    /**
     * 更新现有连接的配置
     * <p>
     * 更新后会自动重连
     * </p>
     *
     * @param name     连接名称
     * @param newConfig 新配置
     * @throws IllegalArgumentException 如果连接不存在
     */
    void updateConnection(String name, McpConnectionConfig newConfig);

    /**
     * 获取指定名称的连接
     *
     * @param name 连接名称
     * @return 连接的 Optional 对象
     */
    Optional<McpConnection> getConnection(String name);

    /**
     * 获取所有连接
     *
     * @return 连接列表
     */
    List<McpConnection> getAllConnections();

    /**
     * 获取所有 MCP 客户端
     *
     * @return MCP 同步客户端列表
     */
    List<McpSyncClient> getAllMcpClients();

    /**
     * 获取所有工具回调
     *
     * @return 工具回调数组
     */
    ToolCallback[] getAllToolCallbacks();

    /**
     * 健康检查指定连接
     *
     * @param name 连接名称
     * @return 是否健康
     */
    boolean healthCheck(String name);

    /**
     * 对所有连接进行健康检查
     *
     * @return 健康检查结果，key 为连接名称，value 为是否健康
     */
    java.util.Map<String, Boolean> healthCheckAll();

    /**
     * 重新连接指定连接
     *
     * @param name 连接名称
     */
    void reconnect(String name);

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    McpStatistics getStatistics();

    /**
     * 启动 MCP 管理器
     * <p>
     * 初始化健康检查任务等
     * </p>
     */
    void start();

    /**
     * 停止 MCP 管理器
     * <p>
     * 关闭所有连接和后台任务
     * </p>
     */
    void stop();

    /**
     * 检查管理器是否已启动
     *
     * @return 是否已启动
     */
    boolean isRunning();
}
