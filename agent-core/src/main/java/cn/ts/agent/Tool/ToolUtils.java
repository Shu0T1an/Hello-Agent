package cn.ts.agent.Tool;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author: ts
 * @description
 * @create: 2026/1/24 21:36
 */
public class ToolUtils {

    public static List<ToolCallback> getAllToolCallbacksFromTools(Object... tools) {

        if (tools == null || tools.length == 0) {
            return List.of();
        }

        ToolCallback[] methodToolCallbacks = getToolCallbacksFromTools(tools);
        ToolCallback[] mcpToolCallbacks = getToolCallbackFromMcp(tools);

        return Stream.concat(
                Arrays.stream(methodToolCallbacks),
                Arrays.stream(mcpToolCallbacks)
        ).toList();
    }



    public static ToolCallback[] getToolCallbacksFromTools(Object... tools) {
        List<Object> filteredTools = new ArrayList<>();

        // 过滤掉 McpSyncClient，只保留普通工具对象
        for (Object tool : tools) {
            if (!(tool instanceof McpSyncClient)) {
                filteredTools.add(tool);
            }
        }

        if (filteredTools.isEmpty()) {
            return new ToolCallback[0];
        }

        ToolCallbackProvider methodToolCallbackProvider = MethodToolCallbackProvider.builder()
                .toolObjects(filteredTools.toArray())
                .build();
        return methodToolCallbackProvider.getToolCallbacks();
    }



    /**
     * 从工具对象数组中提取 MCP 客户端并获取工具回调
     *
     * @param tools 工具对象数组
     * @return MCP 工具回调数组
     */
    public static ToolCallback[] getToolCallbackFromMcp(Object... tools) {
        List<McpSyncClient> mcpSyncClients = new ArrayList<>();
        for (Object tool : tools) {
            if (tool instanceof McpSyncClient mcpSyncClient) {
                mcpSyncClients.add(mcpSyncClient);
            }
        }
        return getToolCallbackFromMcpClients(mcpSyncClients);
    }

    /**
     * 从 MCP 同步客户端列表获取工具回调
     * <p>
     * 使用 Spring AI 的 SyncMcpToolCallbackProvider 实现
     * </p>
     *
     * @param mcpSyncClients MCP 同步客户端列表
     * @return 工具回调数组
     */
    public static ToolCallback[] getToolCallbackFromMcpClients(List<McpSyncClient> mcpSyncClients) {
        if (mcpSyncClients == null || mcpSyncClients.isEmpty()) {
            return new ToolCallback[0];
        }

        SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpSyncClients);
        return provider.getToolCallbacks();
    }



}
