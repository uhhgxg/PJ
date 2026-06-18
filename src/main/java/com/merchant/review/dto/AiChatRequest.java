package com.merchant.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 聊天请求")
public class AiChatRequest {
    @Schema(description = "用户消息", example = "有什么优惠券？")
    private String message;
}
