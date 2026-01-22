package cn.ts.graph;

/**
 * 输出类型枚举
 *
 * @author tianshuo
 */
public enum OutputType {
    /**
     * 纯文本
     */
    TEXT,

    /**
     * 流式文本片段
     */
    CHUNK,

    /**
     * 完整的 ChatResponse
     */
    CHAT_RESPONSE,

    /**
     * 图片
     */
    IMAGE,

    /**
     * 音频
     */
    AUDIO,

    /**
     * 工具调用
     */
    TOOL_CALL,

    /**
     * 元数据
     */
    METADATA,

    /**
     * 节点开始执行
     */
    STARTING,

    /**
     * 节点执行完成
     */
    COMPLETE
}
