package cn.ts.graph;

import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.state.State;

import java.util.Map;

/**
 * 邮件处理示例
 * <p>
 * 展示如何使用 Graph 框架构建一个简单的客服邮件处理流程
 * 参考 Spring AI Alibaba Graph 的示例
 * </p>
 *
 * @author tianshuo
 */
public class EmailProcessingExample {

    public static void main(String[] args) {
        // 1. 构建图
        StateGraph graph = new StateGraph()
                // 添加节点：读取邮件
                .addNode("readEmail", NodeAction.of(state -> {
                    String emailContent = state.value("email").map(Object::toString).orElse("");
                    System.out.println("[readEmail] 处理邮件: " + emailContent);
                    return Map.of("emailContent", emailContent);
                }))
                // 添加节点：分类意图
                .addNode("classify", NodeAction.of(state -> {
                    String content = state.value("emailContent").map(Object::toString).orElse("");
                    String category = content.contains("urgent") ? "urgent" : "normal";
                    System.out.println("[classify] 分类结果: " + category);
                    return Map.of("category", category);
                }))
                // 添加节点：生成回复
                .addNode("respond", NodeAction.of(state -> {
                    String category = state.<String>value("category").orElse("normal");
                    String response = "Thank you. Your " + category + " email was received.";
                    System.out.println("[respond] 生成回复: " + response);
                    return Map.of("response", response);
                }));

        // 2. 连接节点
        graph.addEdge(GraphConstants.START, "readEmail");
        graph.addEdge("readEmail", "classify");
        graph.addEdge("classify", "respond");
        graph.addEdge("respond", GraphConstants.END);


        // 3. 编译并执行
        CompiledGraph app = graph.compile();
        GraphResult result = app.invoke(Map.of("email", "This is urgent! Help me!"));

        // 4. 获取结果
        System.out.println("\n=== 执行结果 ===");
        System.out.println("成功: " + result.isSuccess());
        System.out.println("执行时长: " + result.duration().toMillis() + "ms");
        System.out.println("执行节点数: " + result.executedNodeCount());
        System.out.println("\n最终状态:");
        result.finalState().data().forEach((key, value) ->
                System.out.println("  " + key + " = " + value));

        System.out.println("\n执行历史:");
        result.executionHistory().forEach(execution ->
                System.out.println("  " + execution));
    }

    /**
     * 示例 2：使用条件边
     */
    public static void exampleWithConditionalEdge() {
        System.out.println("\n\n=== 示例 2：条件边 ===\n");

        // 1. 构建带条件边的图
        StateGraph graph = new StateGraph()
                .addNode("classify", NodeAction.of(state -> {
                    String email = state.<String>value("email").orElse("");
                    String category;
                    if (email.contains("urgent")) {
                        category = "urgent";
                    } else if (email.contains("bug")) {
                        category = "bug";
                    } else {
                        category = "normal";
                    }
                    System.out.println("[classify] 分类: " + category);
                    return Map.of("category", category);
                }))
                .addNode("handleUrgent", NodeAction.of(state -> {
                    System.out.println("[handleUrgent] 紧急处理");
                    return Map.of("response", "We'll handle this urgently!");
                }))
                .addNode("handleBug", NodeAction.of(state -> {
                    System.out.println("[handleBug] Bug 处理");
                    return Map.of("response", "Bug ticket created.");
                }))
                .addNode("handleNormal", NodeAction.of(state -> {
                    System.out.println("[handleNormal] 常规处理");
                    return Map.of("response", "Thanks for your email.");
                }));

        // 2. 添加条件边
        graph.addConditionalEdge(
                GraphConstants.START,
                state -> state.value("category", "normal"),
                Map.of(
                        "urgent", "handleUrgent",
                        "bug", "handleBug",
                        "normal", "handleNormal"
                )
        );
        graph.addEdge("handleUrgent", GraphConstants.END);
        graph.addEdge("handleBug", GraphConstants.END);
        graph.addEdge("handleNormal", GraphConstants.END);

        // 3. 测试不同场景
        CompiledGraph app = graph.compile();

        // 场景 1：紧急邮件
        System.out.println("--- 场景 1：紧急邮件 ---");
        GraphResult result1 = app.invoke(Map.of("email", "This is urgent!"));
        System.out.println("回复: " + result1.finalState().value("response"));

        // 场景 2：Bug 报告
        System.out.println("\n--- 场景 2：Bug 报告 ---");
        GraphResult result2 = app.invoke(Map.of("email", "Found a bug in the system"));
        System.out.println("回复: " + result2.finalState().value("response"));

        // 场景 3：普通邮件
        System.out.println("\n--- 场景 3：普通邮件 ---");
        GraphResult result3 = app.invoke(Map.of("email", "Hello, how are you?"));
        System.out.println("回复: " + result3.finalState().value("response"));
    }

    /**
     * 示例 3：多步骤数据处理流程
     */
    public static void exampleDataProcessing() {
        System.out.println("\n\n=== 示例 3：数据处理流程 ===\n");

        StateGraph graph = new StateGraph()
                .addNode("validate", NodeAction.of(state -> {
                    String data = state.value("data", "").toString();
                    boolean valid = !data.isEmpty() && data.length() > 3;
                    System.out.println("[validate] 验证结果: " + valid);
                    return Map.of("valid", valid);
                }))
                .addNode("transform", NodeAction.of(state -> {
                    String data = state.value("data", "").toString();
                    String transformed = data.toUpperCase();
                    System.out.println("[transform] 转换: " + data + " -> " + transformed);
                    return Map.of("transformed", transformed);
                }))
                .addNode("save", NodeAction.of(state -> {
                    String transformed = state.value("transformed", "").toString();
                    System.out.println("[save] 保存数据: " + transformed);
                    return Map.of("saved", true);
                }));

        graph.addEdge(GraphConstants.START, "validate");
        graph.addEdge("validate", "transform");
        graph.addEdge("transform", "save");
        graph.addEdge("save", GraphConstants.END);

        CompiledGraph app = graph.compile();
        GraphResult result = app.invoke(Map.of("data", "hello world"));

        System.out.println("\n最终状态:");
        result.finalState().data().forEach((key, value) ->
                System.out.println("  " + key + " = " + value));
    }

    public static void mainAllExamples(String[] args) {
        main(args);
        exampleWithConditionalEdge();
        exampleDataProcessing();
    }
}
