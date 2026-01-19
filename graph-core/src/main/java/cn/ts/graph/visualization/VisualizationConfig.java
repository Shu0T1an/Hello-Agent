package cn.ts.graph.visualization;

/**
 * 可视化配置类
 * <p>
 * 用于配置图可视化的各种选项
 * </p>
 *
 * @author tianshuo
 */
public class VisualizationConfig {

    /**
     * 图方向
     */
    public enum Direction {
        /**
         * 从上到下
         */
        TOP_DOWN("TD"),
        /**
         * 从下到上
         */
        BOTTOM_UP("BT"),
        /**
         * 从左到右
         */
        LEFT_RIGHT("LR"),
        /**
         * 从右到左
         */
        RIGHT_LEFT("RL");

        private final String code;

        Direction(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final Direction direction;
    private final boolean includeStyles;
    private final boolean showDescriptions;

    private VisualizationConfig(Builder builder) {
        this.direction = builder.direction;
        this.includeStyles = builder.includeStyles;
        this.showDescriptions = builder.showDescriptions;
    }

    /**
     * 获取图方向
     *
     * @return 图方向
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * 是否包含样式
     *
     * @return 如果包含样式返回 true，否则返回 false
     */
    public boolean isIncludeStyles() {
        return includeStyles;
    }

    /**
     * 是否显示节点描述
     *
     * @return 如果显示描述返回 true，否则返回 false
     */
    public boolean isShowDescriptions() {
        return showDescriptions;
    }

    /**
     * 创建默认配置
     *
     * @return 默认配置（从上到下，包含样式，显示描述）
     */
    public static VisualizationConfig createDefault() {
        return new Builder().build();
    }

    /**
     * 创建构建器
     *
     * @return 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 配置构建器
     */
    public static class Builder {
        private Direction direction = Direction.TOP_DOWN;
        private boolean includeStyles = true;
        private boolean showDescriptions = true;

        /**
         * 设置图方向
         *
         * @param direction 图方向
         * @return 当前构建器
         */
        public Builder direction(Direction direction) {
            this.direction = direction;
            return this;
        }

        /**
         * 设置是否包含样式
         *
         * @param includeStyles 是否包含样式
         * @return 当前构建器
         */
        public Builder includeStyles(boolean includeStyles) {
            this.includeStyles = includeStyles;
            return this;
        }

        /**
         * 设置是否显示节点描述
         *
         * @param showDescriptions 是否显示节点描述
         * @return 当前构建器
         */
        public Builder showDescriptions(boolean showDescriptions) {
            this.showDescriptions = showDescriptions;
            return this;
        }

        /**
         * 构建配置
         *
         * @return 配置对象
         */
        public VisualizationConfig build() {
            return new VisualizationConfig(this);
        }
    }
}
