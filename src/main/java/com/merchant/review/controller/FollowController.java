package com.merchant.review.controller;


import com.merchant.review.dto.Result;
import com.merchant.review.service.IFollowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@Slf4j
@Tag(name = "关注")
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        log.info("【请求】{} 用户ID：{}", isFollow ? "关注" : "取关", followUserId);
        return followService.follow(followUserId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        log.info("【请求】查询是否关注，用户ID：{}", followUserId);
        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        log.info("【请求】查询共同关注，目标用户ID：{}", id);
        return followService.followCommons(id);
    }
}
