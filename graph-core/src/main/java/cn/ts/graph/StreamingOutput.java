package cn.ts.graph;

import cn.ts.graph.node.Node;
import cn.ts.graph.state.State;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;

import java.util.Map;
import java.util.Optional;

/**
 * 流式节点输出
 * <p>
 * 用于包装 LLM 流式输出的每个 chunk，同时保留完整的原始数据
 * </p>
 *
 * @param <T> 原始数据的类型（如 ChatResponse, String 等）
 * @author tianshuo
 */
public class StreamingOutput<T> extends NodeOutput {

    private final T originData;        // 原始数据（如完整 ChatResponse）
    private final String chunk;         // 提取的文本片段
    private final OutputType outputType; // 输出类型标识

    private StreamingOutput(
            String nodeId,
            Node node,
            T originData,
            String chunk,
            OutputType outputType,
            State state,
            Usage usage) {
        super(nodeId, node, chunk, state, usage, Map.of());
        this.originData = originData;
        this.chunk = chunk;
        this.outputType = outputType;
    }

    /**
     * 创建一个流式输出
     *
     * @param nodeId     节点ID
     * @param node       节点对象
     * @param originData 原始数据（如完整 ChatResponse）
     * @param chunk      提取的文本片段
     * @param outputType 输出类型
     * @param state      执行后的状态快照
     * @param usage      Token 使用统计（可选）
     * @param <T>        原始数据类型
     * @return StreamingOutput 实例
     */
    public static <T> StreamingOutput<T> of(
            String nodeId,
            Node node,
            T originData,
            String chunk,
            OutputType outputType,
            State state,
            Usage usage) {
        return new StreamingOutput<>(nodeId, node, originData, chunk, outputType, state, usage);
    }

    /**
     * 创建一个 ChatResponse 类型的流式输出
     * <p>
     * 自动从 ChatResponse 中提取文本内容和 usage 信息
     * </p>
     *
     * @param nodeId      节点ID
     * @param node        节点对象
     * @param chatResponse ChatResponse 原始数据
     * @param state       执行后的状态快照
     * @return StreamingOutput 实例
     */
    public static StreamingOutput<ChatResponse> ofChatResponse(
            String nodeId,
            Node node,
            ChatResponse chatResponse,
            State state) {
        String chunk = chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null
                ? chatResponse.getResult().getOutput().getText()
                : "";

        // 提取 usage 信息
        Usage usage = null;
        if (chatResponse.getResult() != null && chatResponse.getMetadata() != null) {
            usage = chatResponse.getMetadata().getUsage();
        }

        return new StreamingOutput<>(nodeId, node, chatResponse, chunk, OutputType.CHAT_RESPONSE, state, usage);
    }

    /**
     * 创建一个纯文本片段的流式输出
     *
     * @param nodeId 节点ID
     * @param node   节点对象
     * @param chunk  文本片段
     * @param state  执行后的状态快照
     * @return StreamingOutput 实例
     */
    public static StreamingOutput<String> ofChunk(
            String nodeId,
            Node node,
            String chunk,
            State state) {
        return new StreamingOutput<>(nodeId, node, chunk, chunk, OutputType.CHUNK, state, null);
    }

    /**
     * 获取原始数据（类型安全）
     *
     * @return 原始数据
     */
    public T getOriginData() {
        return originData;
    }

    /**
     * 获取文本片段
     *
     * @return 文本片段
     */
    public String getChunk() {
        return chunk;
    }

    /**
     * 获取输出类型
     *
     * @return 输出类型
     */
    public OutputType getOutputType() {
        return outputType;
    }

    /**
     * 判断原始数据是否为 ChatResponse
     *
     * @return true 如果原始数据是 ChatResponse
     */
    public boolean isChatResponse() {
        return originData instanceof ChatResponse;
    }

    /**
     * 类型安全的获取原始数据
     *
     * @param type 目标类型
     * @param <C>  类型参数
     * @return Optional 包含类型转换后的数据
     */
    @SuppressWarnings("unchecked")
    public <C> Optional<C> getOriginDataAs(Class<C> type) {
        if (type.isInstance(originData)) {
            return Optional.of((C) originData);
        }
        return Optional.empty();
    }

    /**
     * 便捷方法：获取 ChatResponse
     *
     * @return Optional 包含 ChatResponse
     */
    public Optional<ChatResponse> getChatResponse() {
        return getOriginDataAs(ChatResponse.class);
    }

    @Override
    public String toString() {
        return "StreamingOutput{" +
                "nodeId='" + getNodeId() + '\'' +
                ", outputType=" + outputType +
                ", chunk='" + chunk + '\'' +
                ", originData=" + originData +
                ", state=" + getState() +
                '}';
    }
}
