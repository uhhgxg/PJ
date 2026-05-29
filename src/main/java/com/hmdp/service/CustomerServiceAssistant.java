package com.hmdp.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface CustomerServiceAssistant {

    @SystemMessage("你是黑马点评的智能客服助手。你帮助用户解答关于商铺、优惠券、秒杀活动、订单等问题。请用友好的语气回答，尽量简洁明了。如果用户问的问题超出平台范围，请礼貌地引导用户联系人工客服。")
    String chat(@MemoryId String userId, @UserMessage String message);
}
