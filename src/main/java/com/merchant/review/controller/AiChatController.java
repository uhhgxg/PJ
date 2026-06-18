package com.merchant.review.controller;

import com.merchant.review.dto.AiChatRequest;
import com.merchant.review.dto.Result;
import com.merchant.review.dto.UserDTO;
import com.merchant.review.service.impl.CustomerServiceAssistant;
import com.merchant.review.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@Slf4j
@Tag(name = "AI聊天")
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    @Resource
    private CustomerServiceAssistant assistant;

    @Operation(summary = "AI 聊天")
    @PostMapping("/chat")
    public Result chat(@RequestBody AiChatRequest request) {
        String message = request.getMessage();
        log.info("【请求】AI聊天，消息内容：{}", message);
        if (message == null || message.trim().isEmpty()) {
            log.warn("【响应】消息为空");
            return Result.fail("消息不能为空");
        }
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            log.warn("【响应】用户未登录");
            return Result.fail("请先登录");
        }
        String userId = user.getId().toString();
        try {
            String reply = assistant.chat(userId, message);
            log.info("【响应】AI回复成功，用户ID：{}，回复长度：{}", userId, reply.length());
            return Result.ok(reply);
        } catch (Exception e) {
            log.error("【响应】AI聊天异常：{}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }
}
