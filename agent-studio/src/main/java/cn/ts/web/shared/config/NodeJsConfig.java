package cn.ts.web.shared.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Node.js/NPX 路径配置类
 * 支持跨平台配置 Node.js 执行路径
 *
 * @author tianshuo
 */
@Configuration
@ConfigurationProperties(prefix = "nodejs")
@Data
public class NodeJsConfig {

    /**
     * NPX 命令路径
     * Windows 示例: D:\\Java\\nodejs\\npx.cmd
     * Linux/Mac 示例: /usr/local/bin/npx
     */
    private String npxPath;

    /**
     * Node.js 命令路径（可选）
     */
    private String nodePath;

    /**
     * 获取 NPX 路径，如果未配置则返回系统默认值
     */
    public String getNpxPathOrDefault() {
        if (npxPath != null && !npxPath.isBlank()) {
            return npxPath;
        }
        return getDefaultNpxPath();
    }

    /**
     * 获取系统默认的 NPX 路径
     */
    private String getDefaultNpxPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "npx.cmd";  // Windows 使用 PATH 中的 npx.cmd
        } else {
            return "npx";      // Linux/Mac 使用 PATH 中的 npx
        }
    }
}
