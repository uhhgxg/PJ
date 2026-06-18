package com.merchant.review.service.ai;

import java.util.function.Function;

/**
 * AI 工具函数定义，替代 Spring AI 的 FunctionCallback。
 */
public class AiToolFunction {
    private final String name;
    private final String description;
    private final String jsonSchema;
    private final Function<String, String> handler;

    public AiToolFunction(String name, String description, String jsonSchema,
                          Function<String, String> handler) {
        this.name = name;
        this.description = description;
        this.jsonSchema = jsonSchema;
        this.handler = handler;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getJsonSchema() { return jsonSchema; }
    public String call(String jsonArgs) { return handler.apply(jsonArgs); }
}
