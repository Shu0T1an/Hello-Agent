package cn.ts.graph.record;

/**
 * 基础执行记录实现
 * <p>
 * 用于 CUSTOM 和 END 类型的节点，只包含基础字段
 * </p>
 *
 * @author tianshuo
 */
public class BaseExecutionRecord extends AbstractExecutionRecord {

    /**
     * 构造函数
     */
    public BaseExecutionRecord(
            NodeType nodeType,
            String nodeId,
            String startTime,
            String endTime,
            boolean success,
            String errorMessage) {
        super(nodeType, nodeId,
                java.time.Instant.parse(startTime),
                java.time.Instant.parse(endTime),
                success, errorMessage);
    }
}
