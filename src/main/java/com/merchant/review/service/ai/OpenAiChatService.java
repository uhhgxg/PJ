package com.merchant.review.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OpenAiChatService {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private double temperature;

    @Resource
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 调用 OpenAI 聊天补全 API，支持函数调用。
     *
     * @param messages     消息列表，每个元素包含 "role" 和 "content"
     * @param tools        可用的工具函数列表
     * @return AI 回复文本
     */
    public String chat(List<Map<String, Object>> messages, List<AiToolFunction> tools) {
        String url = baseUrl + "/chat/completions";

        // 构建请求体
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", temperature);
        requestBody.set("messages", convertMessages(messages));

        // 添加工具定义
        if (tools != null && !tools.isEmpty()) {
            requestBody.set("tools", buildToolsArray(tools));
            requestBody.put("tool_choice", "auto");
        }

        // 发送请求
        log.info("【OpenAI】发送请求，model={}，messages={}，tools={}",
                model, messages.size(), tools != null ? tools.size() : 0);
        String responseJson = executeRequest(url, requestBody);
        log.info("【OpenAI】收到原始响应：{}", responseJson);

        // 解析响应
        return parseResponse(responseJson, messages, tools);
    }

    @SuppressWarnings("unchecked")
    private String parseResponse(String responseJson, List<Map<String, Object>> messages,
                                 List<AiToolFunction> tools) {
        try {
            JsonNode root = mapper.readTree(responseJson);
            JsonNode choice = root.get("choices").get(0);
            JsonNode message = choice.get("message");
            String role = message.get("role").asText();
            String content = message.has("content") && !message.get("content").isNull()
                    ? message.get("content").asText() : null;
            JsonNode toolCalls = message.get("tool_calls");

            // 添加 assistant 消息到历史
            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", role);
            assistantMsg.put("content", content);
            if (toolCalls != null) {
                assistantMsg.put("tool_calls", mapper.convertValue(toolCalls, List.class));
            }
            messages.add(assistantMsg);

            // 处理工具调用
            if (toolCalls != null && toolCalls.isArray()) {
                for (JsonNode tc : toolCalls) {
                    String toolName = tc.get("function").get("name").asText();
                    String args = tc.get("function").get("arguments").asText();
                    String callId = tc.get("id").asText();

                    log.info("【OpenAI】执行工具调用：{}，参数：{}", toolName, args);

                    // 执行工具
                    String result = executeTool(toolName, args, tools);

                    // 添加工具结果到历史
                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", callId);
                    toolMsg.put("content", result);
                    messages.add(toolMsg);
                }

                // 递归调用，让 AI 基于工具结果生成回复
                return chat(messages, tools);
            }

            return content != null ? content : "抱歉，我没有理解你的问题。";

        } catch (JsonProcessingException e) {
            log.error("【OpenAI】解析响应失败", e);
            throw new RuntimeException("AI 响应解析失败", e);
        }
    }

    private String executeTool(String toolName, String args, List<AiToolFunction> tools) {
        for (AiToolFunction tool : tools) {
            if (tool.getName().equals(toolName)) {
                try {
                    return tool.call(args);
                } catch (Exception e) {
                    log.error("【OpenAI】工具 {} 执行失败", toolName, e);
                    return "{\"error\":\"工具执行失败: " + e.getMessage() + "\"}";
                }
            }
        }
        log.warn("【OpenAI】未找到工具：{}", toolName);
        return "{\"error\":\"未知工具: " + toolName + "\"}";
    }

    private String executeRequest(String url, ObjectNode requestBody) {
        return restTemplate.postForObject(url, new org.springframework.http.HttpEntity<>(
                requestBody.toString(), createHeaders()), String.class);
    }

    private org.springframework.http.HttpHeaders createHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private ArrayNode convertMessages(List<Map<String, Object>> messages) {
        ArrayNode array = mapper.createArrayNode();
        for (Map<String, Object> msg : messages) {
            ObjectNode node = mapper.createObjectNode();
            node.put("role", (String) msg.get("role"));
            String content = (String) msg.get("content");
            if (content != null) {
                node.put("content", content);
            } else {
                node.putNull("content");
            }

            // 处理 tool_calls（来自 assistant 的响应）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) msg.get("tool_calls");
            if (toolCalls != null) {
                ArrayNode tcArray = mapper.createArrayNode();
                for (Map<String, Object> tc : toolCalls) {
                    ObjectNode tcNode = mapper.createObjectNode();
                    tcNode.put("id", (String) tc.get("id"));
                    tcNode.put("type", (String) tc.get("type"));
                    ObjectNode funcNode = mapper.createObjectNode();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> func = (Map<String, Object>) tc.get("function");
                    funcNode.put("name", (String) func.get("name"));
                    funcNode.put("arguments", (String) func.get("arguments"));
                    tcNode.set("function", funcNode);
                    tcArray.add(tcNode);
                }
                node.set("tool_calls", tcArray);
            }

            // 处理 tool_call_id（来自 tool 角色的消息）
            String toolCallId = (String) msg.get("tool_call_id");
            if (toolCallId != null) {
                node.put("tool_call_id", toolCallId);
            }

            array.add(node);
        }
        return array;
    }

    private ArrayNode buildToolsArray(List<AiToolFunction> tools) {
        ArrayNode array = mapper.createArrayNode();
        for (AiToolFunction tool : tools) {
            ObjectNode toolNode = mapper.createObjectNode();
            toolNode.put("type", "function");
            ObjectNode funcNode = mapper.createObjectNode();
            funcNode.put("name", tool.getName());
            funcNode.put("description", tool.getDescription());
            try {
                funcNode.set("parameters", mapper.readTree(tool.getJsonSchema()));
            } catch (JsonProcessingException e) {
                log.warn("解析工具参数 Schema 失败：{}", tool.getName(), e);
                funcNode.putObject("parameters");
            }
            toolNode.set("function", funcNode);
            array.add(toolNode);
        }
        return array;
    }
}
