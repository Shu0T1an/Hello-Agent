package cn.ts.graph;

import cn.ts.graph.flux.GraphFlux;
import cn.ts.graph.node.AsyncNodeAction;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeActionAsyncUtils;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;

/**
 * 节点执行器
 * <p>
 * 负责执行单个节点并将结果转换为响应式流
 * 支持 CompletableFuture 到 Flux 的转换
 * 参考 Spring AI Alibaba 的 NodeExecutor 设计
 * </p>
 *
 * @author tianshuo
 */
public class NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(NodeExecutor.class);

    /**
     * 执行节点并返回响应式流
     *
     * @param node    要执行的节点
     * @param context 执行上下文
     * @return 响应式流，发射节点执行结果
     *         - 普通节点：GraphResponse<NodeOutput>（状态更新）
     *         - 流式节点：GraphResponse<NodeOutput>（单个流元素）
     */
    public Flux<GraphResponse<NodeOutput>> execute(Node node, GraphRunnerContext context) {
        CompletableFuture<Map<String, Object>> future = executeNodeAsync(node, context);

        return Mono.fromFuture(future)
                .flatMapMany(updates -> handleActionResult(node, context, updates))
                .onErrorResume(error -> Flux.just(GraphResponse.error(error)));
    }

    /**
     * 异步执行节点动作
     *
     * @param node    节点
     * @param context 上下文
     * @return CompletableFuture 包含状态更新
     */
    private CompletableFuture<Map<String, Object>> executeNodeAsync(Node node, GraphRunnerContext context) {
        if (node.action() instanceof AsyncNodeAction asyncAction) {
            return asyncAction.applyAsync(context.getOverallState());
        } else {
            return NodeActionAsyncUtils.async(node.action(), ForkJoinPool.commonPool())
                    .applyAsync(context.getOverallState());
        }
    }

    /**
     * 处理节点执行结果
     *
     * @param node    节点
     * @param context 上下文
     * @param updates 状态更新
     * @return 响应式流
     */
    private Flux<GraphResponse<NodeOutput>> handleActionResult(
            Node node, GraphRunnerContext context, Map<String, Object> updates) {

        // 检测 GraphFlux
        Optional<GraphFlux<?>> flux = extractGraphFlux(updates);
        if (flux.isPresent()) {
            return handleGraphFlux(node, context, flux.get(), updates);
        }

        // 普通结果 - 合并状态
        context.mergeIntoCurrentState(updates);
        // 包装为 NodeOutput
        NodeOutput output = NodeOutput.of(context.getCurrentNodeId(), node, updates, context.getOverallState());
        return Flux.just(GraphResponse.of(context.getCurrentNodeId(), output));
    }

    /**
     * 从状态更新中提取 GraphFlux
     *
     * @param updates 状态更新
     * @return GraphFlux 的 Optional
     */
    private Optional<GraphFlux<?>> extractGraphFlux(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return Optional.empty();
        }

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            if (entry.getValue() instanceof GraphFlux<?> flux) {
                return Optional.of(flux);
            }
        }

        return Optional.empty();
    }

    /**
     * 处理流式结果
     * <p>
     * 检测流元素类型，自动包装为 StreamingOutput
     * 在流结束时自动聚合 ChatResponse 并更新到 state
     * </p>
     *
     * @param node           节点
     * @param context        上下文
     * @param graphFlux      流式结果
     * @param originalUpdates 原始状态更新
     * @return 响应式流
     */
    @SuppressWarnings("unchecked")
    private Flux<GraphResponse<NodeOutput>> handleGraphFlux(
            Node node,
            GraphRunnerContext context,
            GraphFlux<?> graphFlux,
            Map<String, Object> originalUpdates) {

        String nodeName = context.getCurrentNodeId();

        // 创建不含 GraphFlux 的状态更新
        Map<String, Object> filteredUpdates = new HashMap<>();
        if (originalUpdates != null) {
            for (Map.Entry<String, Object> entry : originalUpdates.entrySet()) {
                if (!(entry.getValue() instanceof GraphFlux<?>)) {
                    filteredUpdates.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // 先合并非流式状态
        if (!filteredUpdates.isEmpty()) {
            context.mergeIntoCurrentState(filteredUpdates);
        }

        Flux<Object> stream = (Flux<Object>) graphFlux.getStream();

        // 直接使用 handleChatResponseStream 处理所有流，方法内部会进行类型过滤
        return handleChatResponseStream(nodeName, node, context, stream);
    }

    /**
     * 处理流式输出
     * <p>
     * 同时支持实时流式输出和流完成时的消息聚合。
     * 使用 ConcurrentLinkedQueue 保证线程安全的响应收集。
     * 对于 ChatResponse 类型的元素进行聚合，其他类型直接流式输出。
     * </p>
     */
    private Flux<GraphResponse<NodeOutput>> handleChatResponseStream(
            String nodeName,
            Node node,
            GraphRunnerContext context,
            Flux<Object> stream) {

        // 使用 ConcurrentLinkedQueue 保存响应列表，保证线程安全
        ConcurrentLinkedQueue<ChatResponse> responsesQueue = new ConcurrentLinkedQueue<>();

        return stream
                // 收集 ChatResponse 响应用于聚合（副作用）
                .doOnNext(chunk -> {
                    if (chunk instanceof ChatResponse response) {
                        responsesQueue.add(response);
                    }
                })
                // 实时流式输出
                .map(chunk -> wrapToNodeOutput(nodeName, node, chunk, context))
                // 流完成时聚合并更新 state
                .doOnComplete(() -> {
                    if (!responsesQueue.isEmpty()) {
                        List<Message> messages = aggregateChatResponses(context, new ArrayList<>(responsesQueue));
                        context.mergeIntoCurrentState(Map.of("messages", messages));
                        logger.debug("Aggregated {} ChatResponses into messages", responsesQueue.size());
                    }
                })
                // 流完成信号
                .concatWith(Flux.just(GraphResponse.streamComplete(nodeName)))
                .onErrorResume(error -> {
                    // 流异常时清理资源
                    responsesQueue.clear();
                    return Flux.just(GraphResponse.error(error));
                });
    }

    /**
     * 聚合多个 ChatResponse 为消息列表
     * <p>
     * 从 context 获取原始消息列表，追加聚合后的完整消息
     * </p>
     */
    private List<Message> aggregateChatResponses(GraphRunnerContext context, List<ChatResponse> responses) {
        // 获取原始消息列表
        List<Message> messages = context.getOverallState()
                .<List<Message>>value("messages")
                .orElse(new ArrayList<>());

        // 创建新的消息列表副本
        List<Message> result = new ArrayList<>(messages);

        // 累积所有文本内容
        StringBuilder fullContent = new StringBuilder();
        ChatResponse lastResponse = null;

        for (ChatResponse response : responses) {
            lastResponse = response;
            if (response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null) {
                fullContent.append(response.getResult().getOutput().getText());
            }
        }

        // 创建完整的 AssistantMessage
        if (lastResponse != null && lastResponse.getResult() != null) {
            AssistantMessage fullMessage = new AssistantMessage(
                    fullContent.toString(),
                    lastResponse.getResult().getOutput().getMetadata()
            );
            result.add(fullMessage);
        }

        return result;
    }

    /**
     * 将流元素包装为 NodeOutput
     * <p>
     * 检测元素类型：
     * - ChatResponse → StreamingOutput&lt;ChatResponse&gt;
     * - String → StreamingOutput&lt;String&gt;
     * - 其他 → NodeOutput
     * </p>
     *
     * @param nodeName 节点名称
     * @param node     节点对象
     * @param chunk    流元素
     * @param context  上下文
     * @return GraphResponse
     */
    private GraphResponse<NodeOutput> wrapToNodeOutput(
            String nodeName,
            Node node,
            Object chunk,
            GraphRunnerContext context) {

        NodeOutput output;

        // 检测 ChatResponse 类型
        if (chunk instanceof ChatResponse chatResponse) {
            output = StreamingOutput.ofChatResponse(nodeName, node, chatResponse, context.getOverallState());
        }
        // 检测 String 类型
        else if (chunk instanceof String string) {
            output = StreamingOutput.ofChunk(nodeName, node, string, context.getOverallState());
        }
        // 其他类型使用普通 NodeOutput
        else {
            output = NodeOutput.of(nodeName, node, chunk, context.getOverallState());
        }

        return GraphResponse.stream(nodeName, output);
    }

    /**
     * 创建默认的节点执行器
     *
     * @return NodeExecutor 实例
     */
    public static NodeExecutor create() {
        return new NodeExecutor();
    }
}
