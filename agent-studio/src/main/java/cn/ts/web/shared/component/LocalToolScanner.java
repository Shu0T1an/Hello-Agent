package cn.ts.web.shared.component;

import cn.ts.web.agent.dto.ToolDefinitionDTO;
import cn.ts.web.agent.dto.ToolType;
import cn.ts.web.tool.service.ToolDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地工具扫描器
 * <p>
 * 在应用启动时自动扫描 cn.ts.web.tools 包下所有带有 @Component/@Service 注解的类，
 * 检测其中带有 @Tool 注解的方法，并自动注册到 tool_definition 表。
 * </p>
 */
@Component
public class LocalToolScanner {

    private static final Logger logger = LoggerFactory.getLogger(LocalToolScanner.class);

    private static final String SCAN_PACKAGE = "cn.ts.web.tools";

    private final ApplicationContext applicationContext;
    private final ToolDefinitionService toolDefinitionService;

    public LocalToolScanner(
            ApplicationContext applicationContext,
            ToolDefinitionService toolDefinitionService) {
        this.applicationContext = applicationContext;
        this.toolDefinitionService = toolDefinitionService;
    }

    /**
     * 应用启动完成后自动扫描本地工具
     */


    @EventListener(ApplicationReadyEvent.class)
    public void scanAndRegisterTools() {
        logger.info("Starting local tool scanning in package: {}", SCAN_PACKAGE);

        int scannedCount = 0;
        int registeredCount = 0;
        int errorCount = 0;

        try {
            // 获取所有扫描到的工具
            List<ScannedTool> scannedTools = scanTools();
            scannedCount = scannedTools.size();

            // 注册到数据库
            for (ScannedTool scannedTool : scannedTools) {
                try {
                    registerTool(scannedTool);
                    registeredCount++;
                } catch (Exception e) {
                    logger.error("Failed to register tool: {} - {}",
                            scannedTool.toolName, e.getMessage());
                    errorCount++;
                }
            }

            logger.info("Local tool scanning completed. Scanned: {}, Registered: {}, Errors: {}",
                    scannedCount, registeredCount, errorCount);

        } catch (Exception e) {
            logger.error("Error during local tool scanning: {}", e.getMessage(), e);
        }
    }

    /**
     * 扫描所有本地工具
     */
    private List<ScannedTool> scanTools() {
        List<ScannedTool> scannedTools = new ArrayList<>();

        // 获取所有 Bean
        Map<String, Object> beans = applicationContext.getBeansOfType(Object.class);

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            String beanName = entry.getKey();

            // 检查 Bean 是否来自目标包
            if (!isFromTargetPackage(bean.getClass())) {
                continue;
            }

            // 检查是否有 Component 或 Service 注解
            if (!hasComponentAnnotation(bean.getClass())) {
                continue;
            }

            // 扫描类中的所有方法
            Class<?> targetClass = getTargetClass(bean.getClass());
            Method[] methods = targetClass.getDeclaredMethods();

            for (Method method : methods) {
                // 检查是否有 @Tool 注解
                org.springframework.ai.tool.annotation.Tool toolAnnotation =
                        AnnotationUtils.findAnnotation(method, org.springframework.ai.tool.annotation.Tool.class);

                if (toolAnnotation != null) {
                    ScannedTool scannedTool = createScannedTool(beanName, bean, method, toolAnnotation);
                    scannedTools.add(scannedTool);
//                    logger.debug("Found tool: {} in class: {}", scannedTool.toolName, targetClass.getSimpleName());
                }
            }
        }

        return scannedTools;
    }

    /**
     * 检查类是否来自目标包
     */
    private boolean isFromTargetPackage(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        return pkg != null && pkg.getName().startsWith(SCAN_PACKAGE);
    }

    /**
     * 检查类是否有 @Component、@Service 或 @RestController 注解
     */
    private boolean hasComponentAnnotation(Class<?> clazz) {
        return AnnotationUtils.findAnnotation(clazz, Component.class) != null ||
                AnnotationUtils.findAnnotation(clazz, Service.class) != null ||
                AnnotationUtils.findAnnotation(clazz, RestController.class) != null;
    }

    /**
     * 获取目标类（处理代理类）
     */
    private Class<?> getTargetClass(Class<?> clazz) {
        if (clazz.getName().contains("$$")) {
            // 处理 CGLIB 代理
            Class<?>[] interfaces = clazz.getInterfaces();
            if (interfaces.length > 0) {
                return interfaces[0];
            }
        }
        return clazz;
    }

    /**
     * 创建扫描到的工具信息
     */
    private ScannedTool createScannedTool(String beanName, Object bean, Method method,
                                          org.springframework.ai.tool.annotation.Tool toolAnnotation) {
        String toolName = toolAnnotation.name();
        if (toolName.isEmpty()) {
            toolName = method.getName(); // 使用方法名作为工具名
        }

        String description = toolAnnotation.description();
        if (description.isEmpty()) {
            description = "Tool generated from method: " + method.getName();
        }

        return new ScannedTool(
                toolName,
                beanName,
                bean.getClass().getName(),
                method.getName(),
                description,
                method
        );
    }

    /**
     * 注册工具到数据库
     */
    private void registerTool(ScannedTool scannedTool) {
        ToolDefinitionDTO dto = new ToolDefinitionDTO();
        dto.setToolName(scannedTool.toolName);
        dto.setDisplayName(scannedTool.toolName);
        dto.setDescription(scannedTool.description);
        dto.setToolType(ToolType.LOCAL);
        dto.setClassName(scannedTool.className);
        dto.setIsActive(true);

        // 使用 createOrUpdate 实现 upsert 语义
        ToolDefinitionDTO result = toolDefinitionService.createOrUpdateTool(dto);

        if (result != null) {
            logger.debug("Registered tool: {} ({})",
                    result.getToolName(),
                    result.getId() == null ? "created" : "updated");
        }
    }

    /**
     * 手动触发扫描（用于测试或动态更新）
     */
    public void rescan() {
        logger.info("Manual tool rescan triggered");
        scanAndRegisterTools();
    }

    /**
     * 扫描到的工具信息
     */
    private record ScannedTool(
            String toolName,           // 工具名称
            String beanName,           // Bean 名称
            String className,          // 类名
            String methodName,         // 方法名
            String description,        // 描述
            Method method              // 方法引用
    ) {
    }
}
