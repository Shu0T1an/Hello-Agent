package cn.ts.agent.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 模型请求构建接口
 * <p>
 * 负责将 State 转换为 ChatClient 可识别的请求格式
 * </p>
 *
 * @author tianshuo
 */
public interface ModelRequest {

    /**
     * 构建 ChatClient 请求规范
     *
     * @param chatClient ChatClient 实例
     * @return ChatClientRequestSpec
     */
    ChatClient.ChatClientRequestSpec buildRequest(ChatClient chatClient);

    /**
     * 获取构建的消息列表（用于调试）
     *
     * @return 消息列表
     */
    List<Message> getMessages();
}
