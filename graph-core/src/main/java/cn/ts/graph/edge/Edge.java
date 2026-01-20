package cn.ts.graph.edge;

import java.util.*;

/**
 * 边定义类
 * <p>
 * 封装图中节点间的连接关系
 * 参考 Spring AI Alibaba Graph 的 EdgeValue 设计
 * </p>
 *
 * @author tianshuo
 */
public class Edge {

    private final String from;
    private final EdgeType type;
    private final String to;  // 对于普通边
    private final EdgeAction action;  // 对于条件边
    private final Map<String, String> routeMapping;  // 条件边：路由映射

    private Edge(String from, String to) {
        this.from = from;
        this.to = to;
        this.type = EdgeType.NORMAL;
        this.action = null;
        this.routeMapping = null;
    }

    private Edge(String from, EdgeAction action, Map<String, String> routeMapping) {
        this.from = from;
        this.action = action;
        this.type = EdgeType.CONDITIONAL;
        this.to = null;
        this.routeMapping = routeMapping != null ? new HashMap<>(routeMapping) : null;
    }

    /**
     * 创建一个普通边
     *
     * @param from 源节点标识
     * @param to   目标节点标识
     * @return 普通边对象
     */
    public static Edge of(String from, String to) {
        return new Edge(from, to);
    }

    /**
     * 创建一个条件边
     *
     * @param from         源节点标识
     * @param action       路由动作
     * @param routeMapping 路由映射：条件值 -> 目标节点标识
     * @return 条件边对象
     */
    public static Edge conditional(String from, EdgeAction action, Map<String, String> routeMapping) {
        return new Edge(from, action, routeMapping);
    }

    /**
     * 获取源节点标识
     *
     * @return 源节点标识
     */
    public String from() {
        return from;
    }

    /**
     * 获取目标节点标识（仅适用于普通边）
     *
     * @return 目标节点标识，对于条件边返回 null
     */
    public String to() {
        return to;
    }

    /**
     * 获取边类型
     *
     * @return 边类型
     */
    public EdgeType type() {
        return type;
    }

    /**
     * 获取条件边的路由动作
     *
     * @return 路由动作，对于普通边返回 null
     */
    public EdgeAction action() {
        return action;
    }

    /**
     * 获取条件边的路由映射
     *
     * @return 路由映射的不可修改副本，对于普通边返回 null
     */
    public Map<String, String> routeMapping() {
        return routeMapping != null ? Collections.unmodifiableMap(routeMapping) : null;
    }

    /**
     * 检查是否为普通边
     *
     * @return 如果是普通边返回 true，否则返回 false
     */
    public boolean isNormal() {
        return type == EdgeType.NORMAL;
    }

    /**
     * 检查是否为条件边
     *
     * @return 如果是条件边返回 true，否则返回 false
     */
    public boolean isConditional() {
        return type == EdgeType.CONDITIONAL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        // 对于普通边，比较 from, to, type
        // 对于条件边，比较 from, type, routeMapping
        if (type != edge.type) return false;
        if (!Objects.equals(from, edge.from)) return false;

        if (isNormal()) {
            return Objects.equals(to, edge.to);
        } else { // CONDITIONAL
            return Objects.equals(routeMapping, edge.routeMapping);
        }
    }

    @Override
    public int hashCode() {
        if (isNormal()) {
            return Objects.hash(from, to, type);
        } else {
            return Objects.hash(from, type, routeMapping);
        }
    }

    @Override
    public String toString() {
        if (isNormal()) {
            return "Edge{" +
                    "from='" + from + '\'' +
                    ", to='" + to + '\'' +
                    ", type=" + type +
                    '}';
        } else {
            return "Edge{" +
                    "from='" + from + '\'' +
                    ", type=" + type +
                    ", routeMapping=" + routeMapping +
                    '}';
        }
    }
}
