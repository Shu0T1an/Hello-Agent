package cn.ts.graph.visualization;

import cn.ts.graph.CompiledGraph;
import cn.ts.graph.StateGraph;

/**
 * 图可视化器接口
 * <p>
 * 用于将图结构转换为可视化的字符串表示，如 Mermaid 格式
 * </p>
 *
 * @author tianshuo
 */
public interface GraphVisualizer {

    /**
     * 将 StateGraph 可视化为字符串
     *
     * @param graph 状态图
     * @return 可视化字符串
     */
    String visualize(StateGraph graph);

    /**
     * 将 StateGraph 可视化为字符串（带配置）
     *
     * @param graph  状态图
     * @param config 可视化配置
     * @return 可视化字符串
     */
    String visualize(StateGraph graph, VisualizationConfig config);

    /**
     * 将 CompiledGraph 可视化为字符串
     *
     * @param graph 编译后的图
     * @return 可视化字符串
     */
    String visualize(CompiledGraph graph);

    /**
     * 将 CompiledGraph 可视化为字符串（带配置）
     *
     * @param graph  编译后的图
     * @param config 可视化配置
     * @return 可视化字符串
     */
    String visualize(CompiledGraph graph, VisualizationConfig config);
}
