package com.merchant.review.service.impl;

import com.merchant.review.dto.Result;
import com.merchant.review.entity.ShopType;
import com.merchant.review.mapper.ShopTypeMapper;
import com.merchant.review.service.IShopTypeService;
import com.merchant.review.utils.RedisUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.merchant.review.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.merchant.review.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private RedisUtils redisUtils;

    private static final String SHOP_TYPE_LIST_KEY = CACHE_SHOP_TYPE_KEY + "list";

    @Override
    public Result queryList() {
        log.info("【查询店铺类型】开始查询店铺类型列表");
        // 先从Redis缓存中查询
        List<ShopType> list = redisUtils.getList(SHOP_TYPE_LIST_KEY, ShopType.class);
        if (list != null && !list.isEmpty()) {
            log.info("【查询店铺类型】缓存命中，共 {} 条", list.size());
            return Result.ok(list);
        }
        log.info("【查询店铺类型】缓存未命中，从DB查询");
        // 缓存未命中，从数据库查询并按排序字段升序排列
        list = query().orderByAsc("sort").list();
        if (list == null || list.isEmpty()) {
            log.warn("【查询店铺类型】数据库中也无数据");
            return Result.fail("店铺类型列表不存在！");
        }
        // 将查询结果写入Redis缓存
        redisUtils.set(SHOP_TYPE_LIST_KEY, list, CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        log.info("【查询店铺类型】从DB查到 {} 条，已写入缓存", list.size());
        return Result.ok(list);
    }
}
