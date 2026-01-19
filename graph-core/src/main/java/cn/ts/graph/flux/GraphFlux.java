package cn.ts.graph.flux;

import reactor.core.publisher.Flux;

/**
 * 流式结果包装类
 * <p>
 * 用于包装需要流式发射的响应式数据流
 * 参考 Spring AI Alibaba 的 GraphFlux 设计
 * </p>
 *
 * @param <T> 流中元素的类型
 * @author tianshuo
 */
public class GraphFlux<T> {

    private final String nodeName;
    private final Flux<T> stream;

    private GraphFlux(String nodeName, Flux<T> stream) {
        this.nodeName = nodeName;
        this.stream = stream;
    }

    /**
     * 创建一个 GraphFlux 实例
     *
     * @param nodeName 节点名称
     * @param stream   响应式流
     * @param <T>      流中元素的类型
     * @return GraphFlux 实例
     */
    public static <T> GraphFlux<T> of(String nodeName, Flux<T> stream) {
        return new GraphFlux<>(nodeName, stream);
    }

    /**
     * 获取节点名称
     *
     * @return 节点名称
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * 获取响应式流
     *
     * @return 响应式流
     */
    public Flux<T> getStream() {
        return stream;
    }
}
