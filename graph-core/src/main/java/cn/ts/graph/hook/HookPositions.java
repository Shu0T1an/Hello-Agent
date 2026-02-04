package cn.ts.graph.hook;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Hook 位置注解
 * <p>
 * 用于标注 Hook 实现类在哪些位置执行
 * </p>
 *
 * @author tianshuo
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HookPositions {
    /**
     * Hook 位置数组
     *
     * @return Hook 位置数组
     */
    HookPosition[] value();
}
