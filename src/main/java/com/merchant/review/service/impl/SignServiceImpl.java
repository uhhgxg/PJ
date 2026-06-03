package com.merchant.review.service.impl;

import com.merchant.review.dto.Result;
import com.merchant.review.service.ISignService;
import com.merchant.review.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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

        // 检查今日是否已签到
        Boolean isSigned = stringRedisTemplate.opsForValue().getBit(key, dayOfMonth - 1);
        if (Boolean.TRUE.equals(isSigned)) {
            return Result.fail("今日已签到");
        }

        // SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        log.debug("用户 {} 签到成功，日期 {}", userId, now);
        return Result.ok();
    }

    @Override
    public Result getSignRecords() {
        Long userId = UserHolder.getUser().getId();
        LocalDate now = LocalDate.now();
        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();

        // BITFIELD key GET u{dayOfMonth} 0 — 拉取本月截至今日的所有比特位
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );

        // 无签到记录
        if (result == null || result.isEmpty() || result.get(0) == null) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("signDates", Collections.emptyList());
            empty.put("consecutiveDays", 0);
            empty.put("totalSignDays", 0);
            return Result.ok(empty);
        }

        long bits = result.get(0);

        // 计算连续签到天数（从今天往前）
        int consecutiveDays = 0;
        for (int i = dayOfMonth - 1; i >= 0; i--) {
            if ((bits >> i & 1) == 1) {
                consecutiveDays++;
            } else {
                break;
            }
        }

        // 构建签到日期列表
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

        return Result.ok(resultMap);
    }
}
