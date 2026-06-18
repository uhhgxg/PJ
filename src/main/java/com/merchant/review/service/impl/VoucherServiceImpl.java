package com.merchant.review.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.merchant.review.dto.Result;
import com.merchant.review.entity.Voucher;
import com.merchant.review.mapper.VoucherMapper;
import com.merchant.review.entity.SeckillVoucher;
import com.merchant.review.service.ISeckillVoucherService;
import com.merchant.review.service.IVoucherService;
import com.merchant.review.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.ZoneOffset;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 1. 保存优惠券到 DB
        save(voucher);
        log.info("优惠券保存成功 voucherId={} title={}", voucher.getId(), voucher.getTitle());

        // 2. 保存秒杀信息到 DB
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        log.info("秒杀信息保存成功 voucherId={} stock={} begin={} end={}",
                voucher.getId(), voucher.getStock(), voucher.getBeginTime(), voucher.getEndTime());

        // 3. Redis 库存预热 + 已购用户 Set 清理 + 活动时间元数据写入（使用 Redisson）
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucher.getId();
        String orderKey = RedisConstants.SECKILL_ORDER_KEY + voucher.getId();
        String metaKey = RedisConstants.SECKILL_VOUCHER_KEY + voucher.getId();

        // 库存写入 Redis（RAtomicLong）
        RAtomicLong stock = redissonClient.getAtomicLong(stockKey);
        stock.set(voucher.getStock());
        // 清理已购用户 Set（新优惠券，不残留历史数据）
        RSet<String> orderSet = redissonClient.getSet(orderKey);
        orderSet.delete();
        // 活动时间写入 Redis（格式："beginEpochSecond,endEpochSecond"）
        String metaValue = voucher.getBeginTime().toEpochSecond(ZoneOffset.UTC)
                + "," + voucher.getEndTime().toEpochSecond(ZoneOffset.UTC);
        RBucket<String> metaBucket = redissonClient.getBucket(metaKey);
        metaBucket.set(metaValue);

        log.info("Redis 库存预热完成 stockKey={}={} orderKey={}=已清理 metaKey={}={}",
                stockKey, voucher.getStock(), orderKey, metaKey, metaValue);
    }
}
