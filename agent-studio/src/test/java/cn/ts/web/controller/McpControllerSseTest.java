package cn.ts.web.controller;

import cn.ts.agent.mcp.model.McpConnectionType;
import cn.ts.web.infra.mcp.dto.McpConnectionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MCP 控制器 SSE 集成测试
 * <p>
 * 测试通过 REST API 创建和管理 SSE 类型的 MCP 连接
 * </p>
 *
 * @author tianshuo
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class McpControllerSseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String testConnectionName;
    private static int testCounter = 0;

    @BeforeEach
    void setUp() {
        testCounter++;
        testConnectionName = "test-sse-conn-" + System.currentTimeMillis() + "-" + testCounter;
    }

    @AfterEach
    void tearDown() throws Exception {
        // 尝试清理测试连接
        try {
            mockMvc.perform(delete("/api/mcp/connections/" + testConnectionName));
        } catch (Exception e) {
            // 忽略删除失败
        }
    }

    @Test
    void testCreateSseConnection() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer test-token");

        McpConnectionRequest request = new McpConnectionRequest();
        request.setName(testConnectionName);
        request.setType(McpConnectionType.SSE);
        request.setDescription("测试 SSE 连接");
        request.setSseUrl("http://localhost:3000/sse");
        request.setSseHeaders(headers);
        request.setTimeoutSeconds(30);
        request.setAutoReconnect(true);
        request.setMaxRetries(3);
        request.setRetryIntervalSeconds(5);

        mockMvc.perform(post("/api/mcp/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.name").value(testConnectionName));
    }

    @Test
    void testCreateSseConnectionWithoutHeaders() throws Exception {
        McpConnectionRequest request = new McpConnectionRequest();
        request.setName(testConnectionName + "-no-headers");
        request.setType(McpConnectionType.SSE);
        request.setDescription("测试不带请求头的 SSE 连接");
        request.setSseUrl("http://localhost:3000/sse");
        request.setTimeoutSeconds(30);
        request.setAutoReconnect(true);
        request.setMaxRetries(3);
        request.setRetryIntervalSeconds(5);

        mockMvc.perform(post("/api/mcp/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value(testConnectionName + "-no-headers"));
    }

    @Test
    void testCreateHttpConnection() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        McpConnectionRequest request = new McpConnectionRequest();
        request.setName(testConnectionName + "-http");
        request.setType(McpConnectionType.HTTP);
        request.setDescription("测试 HTTP 连接（预留）");
        request.setHttpUrl("http://localhost:3000/api");
        request.setHttpHeaders(headers);
        request.setHttpMethod("POST");
        request.setTimeoutSeconds(30);
        request.setAutoReconnect(true);
        request.setMaxRetries(3);
        request.setRetryIntervalSeconds(5);

        // HTTP 类型暂不支持，但可以注册
        mockMvc.perform(post("/api/mcp/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value(testConnectionName + "-http"));
    }

    @Test
    void testGetSseConnection() throws Exception {
        // 先创建连接
        McpConnectionRequest request = new McpConnectionRequest();
        request.setName(testConnectionName + "-get");
        request.setType(McpConnectionType.SSE);
        request.setDescription("测试获取 SSE 连接");
        request.setSseUrl("http://localhost:3000/sse");
        request.setTimeoutSeconds(30);
        request.setAutoReconnect(true);
        request.setMaxRetries(3);
        request.setRetryIntervalSeconds(5);

        mockMvc.perform(post("/api/mcp/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // 等待连接创建
        Thread.sleep(100);

        // 获取连接
        mockMvc.perform(get("/api/mcp/connections/" + testConnectionName + "-get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value(testConnectionName + "-get"))
                .andExpect(jsonPath("$.data.type").value("SSE"));
    }

    @Test
    void testUpdateSseConnection() throws Exception {
        // 先创建连接
        McpConnectionRequest request = new McpConnectionRequest();
        request.setName(testConnectionName + "-update");
        request.setType(McpConnectionType.SSE);
        request.setDescription("原始描述");
        request.setSseUrl("http://localhost:3000/sse");
        request.setTimeoutSeconds(30);
        request.setAutoReconnect(true);
        request.setMaxRetries(3);
        request.setRetryIntervalSeconds(5);

        mockMvc.perform(post("/api/mcp/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // 等待连接创建
        Thread.sleep(100);

        // 更新连接
        Map<String, String> newHeaders = new HashMap<>();
        newHeaders.put("Authorization", "Bearer new-token");

        request.setDescription("更新后的描述");
        request.setSseUrl("http://localhost:3001/sse");
        request.setSseHeaders(newHeaders);

        mockMvc.perform(put("/api/mcp/connections/" + testConnectionName + "-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void testDeleteSseConnection() throws Exception {
        // 先创建连接
        String deleteTestName = testConnectionName + "-delete";
        McpConnectionRequest request = new McpConnectionRequest();
        request.setName(deleteTestName);
        request.setType(McpConnectionType.SSE);
        request.setDescription("测试删除 SSE 连接");
        request.setSseUrl("http://localhost:3000/sse");
        request.setTimeoutSeconds(30);
        request.setAutoReconnect(true);
        request.setMaxRetries(3);
        request.setRetryIntervalSeconds(5);

        mockMvc.perform(post("/api/mcp/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // 等待连接创建
        Thread.sleep(100);

        // 删除连接
        mockMvc.perform(delete("/api/mcp/connections/" + deleteTestName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证连接已删除
        mockMvc.perform(get("/api/mcp/connections/" + deleteTestName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    @Test
    void testListAllConnectionsIncludingSse() throws Exception {
        // 创建 SSE 连接
        McpConnectionRequest sseRequest = new McpConnectionRequest();
        sseRequest.setName(testConnectionName + "-sse-list");
        sseRequest.setType(McpConnectionType.SSE);
        sseRequest.setDescription("SSE 连接列表测试");
        sseRequest.setSseUrl("http://localhost:3000/sse");
        sseRequest.setTimeoutSeconds(30);
        sseRequest.setAutoReconnect(true);
        sseRequest.setMaxRetries(3);
        sseRequest.setRetryIntervalSeconds(5);

        mockMvc.perform(post("/api/mcp/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sseRequest)));

        // 等待连接创建
        Thread.sleep(100);

        // 列出所有连接
        mockMvc.perform(get("/api/mcp/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasItem(hasEntry("name", testConnectionName + "-sse-list"))));
    }

    @Test
    void testSseConnectionHealthCheck() throws Exception {
        // 先创建连接
        McpConnectionRequest request = new McpConnectionRequest();
        request.setName(testConnectionName + "-health");
        request.setType(McpConnectionType.SSE);
        request.setDescription("测试 SSE 连接健康检查");
        request.setSseUrl("http://localhost:3000/sse");
        request.setTimeoutSeconds(30);
        request.setAutoReconnect(true);
        request.setMaxRetries(3);
        request.setRetryIntervalSeconds(5);

        mockMvc.perform(post("/api/mcp/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // 等待连接创建
        Thread.sleep(100);

        // 执行健康检查
        mockMvc.perform(post("/api/mcp/connections/" + testConnectionName + "-health/health-check"))
                .andExpect(status().isOk());
    }

    @Test
    void testCreateConnectionWithDuplicateName() throws Exception {
        McpConnectionRequest request = new McpConnectionRequest();
        request.setName(testConnectionName + "-dup");
        request.setType(McpConnectionType.SSE);
        request.setDescription("测试重复名称");
        request.setSseUrl("http://localhost:3000/sse");
        request.setTimeoutSeconds(30);
        request.setAutoReconnect(true);
        request.setMaxRetries(3);
        request.setRetryIntervalSeconds(5);

        // 第一次创建
        mockMvc.perform(post("/api/mcp/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 等待连接创建
        Thread.sleep(100);

        // 第二次创建（应该失败）
        mockMvc.perform(post("/api/mcp/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(200)));
    }
}
