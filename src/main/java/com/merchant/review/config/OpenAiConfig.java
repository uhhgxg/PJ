package com.merchant.review.config;

import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class OpenAiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double temperature;

    @Bean
    public OpenAiApi openAiApi() {
        return new OpenAiApi(baseUrl, apiKey);
    }

    @Bean
    public FunctionCallbackContext functionCallbackContext(ApplicationContext applicationContext) {
        FunctionCallbackContext context = new FunctionCallbackContext();
        context.setApplicationContext(applicationContext);
        return context;
    }

    @Bean
    public OpenAiChatClient openAiChatClient(OpenAiApi openAiApi,
                                             FunctionCallbackContext functionCallbackContext) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature.floatValue())
                .build();
        return new OpenAiChatClient(openAiApi, options, functionCallbackContext,
                RetryTemplate.builder().maxAttempts(3).build());
    }
}
