package com.merchant.review.controller;

import com.merchant.review.dto.Result;
import com.merchant.review.service.CustomerServiceAssistant;
import com.merchant.review.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    @Resource
    private CustomerServiceAssistant assistant;

    @PostMapping("/chat")
    public Result chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Result.fail("消息不能为空");
        }
        String userId = UserHolder.getUser().getId().toString();
        String reply = assistant.chat(userId, message);
        return Result.ok(reply);
    }
}
