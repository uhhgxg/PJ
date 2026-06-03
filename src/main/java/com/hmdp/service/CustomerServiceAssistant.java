package com.hmdp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CustomerServiceAssistant {

    private static final String SYSTEM_PROMPT = "你是黑马点评的智能客服助手。你帮助用户解答关于商铺、优惠券、秒杀活动、订单等问题。请用友好的语气回答，尽量简洁明了。如果用户问的问题超出平台范围，请礼貌地引导用户联系人工客服。";

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.openai.base-url}")
    private String baseUrl;

    @Value("${ai.openai.model:gpt-3.5-turbo}")
    private String model;

    @Value("${ai.openai.temperature:0.7}")
    private Double temperature;

    @Resource
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    // In-memory conversation history per user
    private final Map<String, List<Map<String, String>>> conversations = new ConcurrentHashMap<>();

    public String chat(String userId, String message) {
        conversations.putIfAbsent(userId, new ArrayList<>());
        List<Map<String, String>> history = conversations.get(userId);

        // Build messages array
        ArrayNode messages = mapper.createArrayNode();

        // System prompt
        ObjectNode systemNode = mapper.createObjectNode();
        systemNode.put("role", "system");
        systemNode.put("content", SYSTEM_PROMPT);
        messages.add(systemNode);

        // Conversation history
        for (Map<String, String> msg : history) {
            ObjectNode node = mapper.createObjectNode();
            node.put("role", msg.get("role"));
            node.put("content", msg.get("content"));
            messages.add(node);
        }

        // Current user message
        ObjectNode userNode = mapper.createObjectNode();
        userNode.put("role", "user");
        userNode.put("content", message);
        messages.add(userNode);

        // Build request body
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.set("messages", messages);
        requestBody.put("temperature", temperature);

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions", request, String.class);

            JsonNode root = mapper.readTree(response.getBody());
            String reply = root.get("choices").get(0).get("message").get("content").asText();

            // Save to conversation history
            history.add(Map.of("role", "user", "content", message));
            history.add(Map.of("role", "assistant", "content", reply));

            return reply;
        } catch (Exception e) {
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }
}
