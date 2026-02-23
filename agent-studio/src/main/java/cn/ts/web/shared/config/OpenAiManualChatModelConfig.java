package cn.ts.web.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(
        prefix = "app.llm.openai-manual",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OpenAiManualChatModelConfig {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiManualChatModelConfig.class);

    @Bean
    @Primary
    public ChatModel manualOpenAiCompatibleChatModel(
            @Value("${app.llm.openai-manual.base-url}") String baseUrl,
            @Value("${app.llm.openai-manual.api-key}") String apiKey,
            @Value("${app.llm.openai-manual.completions-path:/chat/completions}") String completionsPath,
            @Value("${app.llm.openai-manual.model:gpt-4o-mini}") String model
            ) {

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("app.llm.openai-manual.base-url must not be blank");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("app.llm.openai-manual.api-key must not be blank");
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(normalizePath(completionsPath))
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
                .build();

        logger.info("Configured manual OpenAI-compatible ChatModel: baseUrl={}, completionsPath={}, model={}",
                baseUrl, normalizePath(completionsPath), model);
        return chatModel;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/chat/completions";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
