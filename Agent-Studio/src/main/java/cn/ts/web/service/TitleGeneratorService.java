package cn.ts.web.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * 会话标题生成服务
 * <p>
 * 根据用户消息生成简洁的会话标题
 * </p>
 *
 * @author tianshuo
 */
@Service
public class TitleGeneratorService {

    private final ChatModel chatModel;

    public TitleGeneratorService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 根据用户消息生成会话标题
     *
     * @param userMessage 用户消息
     * @return 生成的标题（限制10个汉字以内）
     */
    public String generateTitle(String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) {
            return "新对话";
        }

        // 截取前100个字符作为上下文
        String context = userMessage.length() > 100
                ? userMessage.substring(0, 100)
                : userMessage;

        String prompt = String.format("""
                请根据以下用户消息，生成一个简洁的会话标题。
                要求：
                1. 标题长度不超过10个汉字
                2. 简洁明了，能概括对话主题
                3. 不要使用标点符号
                4. 直接返回标题，不要有任何解释

                用户消息：%s

                标题：""", context);

        try {
            String response = chatModel.call(prompt);
            String title = response.trim();
            // 清理可能的前缀和后缀
            title = title.replaceAll("^(标题[:：]?|名称[:：]?)", "")
                    .replaceAll("^[\"'`]+|[\"'`]+$", "")
                    .trim();
            // 限制长度
            if (title.length() > 15) {
                title = title.substring(0, 15);
            }
            return title.isEmpty() ? "新对话" : title;
        } catch (Exception e) {
            // 降级：使用前10个字符作为标题
            return userMessage.length() > 10
                    ? userMessage.substring(0, 10) + "..."
                    : userMessage;
        }
    }
}
