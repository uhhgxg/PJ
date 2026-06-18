package com.merchant.review.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class CustomerServiceAssistant {

    private static final String SYSTEM_PROMPT = "你是商户评价系统的智能客服助手。\n\n" +
            "你可以通过调用函数来查询实时业务数据。当用户问到以下内容时，请调用对应的函数获取数据：\n" +
            "1. 商铺分类：调用 getShopTypes\n" +
            "2. 搜索商铺：调用 searchShops\n" +
            "3. 商铺详情（评分、地址等）：调用 getShopDetail\n" +
            "4. 附近商铺：调用 getNearbyShops（需要分类名称和坐标）\n" +
            "5. 优惠券信息：调用 getShopVouchers\n" +
            "6. 秒杀活动：调用 getActiveSeckillVouchers\n\n" +
            "注意：商铺评分 score 字段需要除以10才是真实评分（如37表示3.7分）。回答简洁自然。";

    private static final String HISTORY_KEY_PREFIX = "chat:history:";
    private static final int MAX_ROUNDS = 20;

    @Resource
    private OpenAiChatClient chatClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    public String chat(String userId, String message) {
        log.info("【AI聊天】用户 {} 发送消息：{}", userId, message);

        // 1. 从 Redis 读取历史对话
        String redisKey = HISTORY_KEY_PREFIX + userId;
        List<String> historyJsonList = stringRedisTemplate.opsForList().range(redisKey, 0, -1);
        List<Message> historyMessages = new ArrayList<>();
        if (historyJsonList != null) {
            for (String msgJson : historyJsonList) {
                try {
                    JsonNode node = mapper.readTree(msgJson);
                    String role = node.get("role").asText();
                    String content = node.get("content").asText();
                    historyMessages.add("user".equals(role)
                            ? new UserMessage(content)
                            : new AssistantMessage(content));
                } catch (Exception e) {
                    log.warn("解析历史消息失败，跳过：{}", msgJson);
                }
            }
        }

        // 2. 构建消息列表
        List<Message> allMessages = new ArrayList<>();
        allMessages.add(new SystemMessage(SYSTEM_PROMPT));
        allMessages.addAll(historyMessages);
        allMessages.add(new UserMessage(message));

        // 3. 配置函数调用
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withFunctions(Set.of(
                        "getShopTypes", "searchShops", "getShopDetail",
                        "getNearbyShops", "getShopVouchers", "getActiveSeckillVouchers"))
                .build();

        // 4. 调用 AI
        Prompt prompt = new Prompt(allMessages, options);
        String reply;
        try {
            ChatResponse response = chatClient.call(prompt);
            reply = response.getResult().getOutput().getContent();
            log.info("【AI聊天】回复成功，长度：{}", reply != null ? reply.length() : 0);
        } catch (Exception e) {
            log.error("【AI聊天】调用AI失败：{}", e.getMessage());
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }

        if (reply == null) {
            reply = "抱歉，我没有理解你的问题，请重新描述。";
        }

        // 5. 保存对话历史
        try {
            String userMsgJson = mapper.createObjectNode()
                    .put("role", "user").put("content", message).toString();
            String assistantMsgJson = mapper.createObjectNode()
                    .put("role", "assistant").put("content", reply).toString();
            stringRedisTemplate.opsForList().rightPushAll(redisKey, userMsgJson, assistantMsgJson);
            stringRedisTemplate.opsForList().trim(redisKey, -(long) MAX_ROUNDS * 2, -1);
        } catch (Exception e) {
            log.warn("保存对话历史失败：{}", e.getMessage());
        }

        return reply;
    }
}
