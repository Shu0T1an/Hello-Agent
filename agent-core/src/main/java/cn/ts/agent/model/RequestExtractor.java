package cn.ts.agent.model;

import cn.ts.graph.state.State;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;

/**
 * 请求数据提取器接口（预留）
 * <p>
 * 未来可用于责任链模式，从 State 中提取特定数据
 * </p>
 *
 * @author tianshuo
 */
@FunctionalInterface
public interface RequestExtractor {

    /**
     * 从 State 中提取请求数据
     *
     * @param state 当前状态
     * @return 提取的数据上下文
     */
    RequestContext extract(State state);

    /**
     * 数据上下文
     *
     * @param messages 消息列表
     * @param systemPrompt 系统提示词
     * @param params 其他参数
     */
    record RequestContext(
            List<Message> messages,
            String systemPrompt,
            Map<String, Object> params
    ) {}
}
