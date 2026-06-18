package com.merchant.review.service.impl;

import com.merchant.review.dto.Result;
import com.merchant.review.service.ISignService;
import com.merchant.review.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.merchant.review.utils.RedisConstants.USER_SIGN_KEY;

/**
 * 签到服务实现
 * <p>
 * Redis BitMap key 格式：sign:{userId}:{yyyyMM}
 * offset = dayOfMonth - 1（0-based）
 * <p>
 * 连续签到天数通过 BITFIELD 拉取整月比特位后从当日起反向遍历计算。
 */
@Slf4j
@Service
public class SignServiceImpl implements ISignService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();
        log.info("【签到】用户 {} 开始签到，日期：{}，Redis Key：{}", userId, now, key);

        // 使用Redis BitMap检查今日是否已签到（利用GETBIT命令）
        Boolean isSigned = stringRedisTemplate.opsForValue().getBit(key, dayOfMonth - 1);
        if (Boolean.TRUE.equals(isSigned)) {
            log.warn("【签到】用户 {} 今日已签到，不能重复签到", userId);
            return Result.fail("今日已签到");
        }

        // 执行SETBIT命令，将当天对应的bit位设为1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        log.info("【签到】用户 {} 签到成功，日期 {}，月内第 {} 天", userId, now, dayOfMonth);
        return Result.ok();
    }

    @Override
    public Result getSignRecords() {
        Long userId = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();
        log.info("【查询签到】查询用户 {} 本月签到记录，日期：{}", userId, now);

        // BITFIELD key GET u{dayOfMonth} 0 — 从Redis BitMap中拉取本月截至今日的所有比特位
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );

        // 无签到记录
        if (result == null || result.isEmpty() || result.get(0) == null) {
            log.info("【查询签到】本月暂无签到记录");
            Map<String, Object> empty = new HashMap<>();
            empty.put("signDates", Collections.emptyList());
            empty.put("consecutiveDays", 0);
            empty.put("totalSignDays", 0);
            return Result.ok(empty);
        }

        long bits = result.get(0);

        // 从今天往前遍历，计算连续签到天数（遇到未签到的天就停止）
        int consecutiveDays = 0;
        for (int i = dayOfMonth - 1; i >= 0; i--) {
            if ((bits >> i & 1) == 1) {
                consecutiveDays++;
            } else {
                break;
            }
        }

        // 构建本月所有签到日期列表
        List<String> signDates = new ArrayList<>();
        for (int i = 0; i < dayOfMonth; i++) {
            if ((bits >> i & 1) == 1) {
                signDates.add(now.withDayOfMonth(i + 1).toString());
            }
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("signDates", signDates);
        resultMap.put("consecutiveDays", consecutiveDays);
        resultMap.put("totalSignDays", Long.bitCount(bits));

        log.info("【查询签到】本月签到 {} 天，连续签到 {} 天，签到日期：{}", Long.bitCount(bits), consecutiveDays, signDates);
        return Result.ok(resultMap);
    }
}
