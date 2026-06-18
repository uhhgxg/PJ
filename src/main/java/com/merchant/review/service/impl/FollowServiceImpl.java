package com.merchant.review.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.merchant.review.dto.Result;
import com.merchant.review.dto.UserDTO;
import com.merchant.review.entity.Follow;
import com.merchant.review.mapper.FollowMapper;
import com.merchant.review.service.IFollowService;
import com.merchant.review.service.IUserService;
import com.merchant.review.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        log.info("【关注操作】用户 {} 开始 {} 用户 {}", userId, isFollow ? "关注" : "取关", followUserId);
        String key = "follows:" + userId;
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
                log.info("【关注操作】关注成功，用户 {} → 用户 {}", userId, followUserId);
            } else {
                log.warn("【关注操作】关注失败，DB写入失败");
            }
        } else {
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
                log.info("【关注操作】取关成功，用户 {} → 用户 {}", userId, followUserId);
            } else {
                log.warn("【关注操作】取关失败，DB记录不存在");
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        log.info("【查询关注】用户 {} 是否关注用户 {}：{}", userId, followUserId, count > 0);
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        log.info("【共同关注】查询用户 {} 和用户 {} 的共同关注", userId, id);
        String key = "follows:" + userId;
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            log.info("【共同关注】没有共同关注的人");
            return Result.ok(Collections.emptyList());
        }
        log.info("【共同关注】找到 {} 个共同关注", intersect.size());
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
