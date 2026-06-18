package com.merchant.review.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.merchant.review.dto.Result;
import com.merchant.review.entity.Shop;
import com.merchant.review.mapper.ShopMapper;
import com.merchant.review.service.IShopService;
import com.merchant.review.utils.CacheClient;
import com.merchant.review.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.merchant.review.utils.RedisConstants.*;

@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        log.info("【查询商户】查询商铺详情，商铺ID：{}", id);
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, this::getById,
                CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            log.warn("【查询商户】商铺不存在，ID：{}", id);
            return Result.fail("店铺信息不存在！");
        }
        log.info("【查询商户】查询成功，商铺名称：{}，ID：{}", shop.getName(), id);
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        log.info("【更新商户】开始更新商铺，ID：{}", id);
        if (id == null) {
            log.warn("【更新商户】商铺ID为空");
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        // 更新DB后删除Redis缓存，下次查询时自动重建
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        log.info("【更新商户】更新成功，Redis缓存已清除，商铺ID：{}", id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        log.info("【按类型查询商户】类型ID：{}，第{}页，坐标：({}, {})", typeId, current, x, y);
        if (x == null || y == null) {
            // 没有坐标，按类型分页查询（普通列表模式）
            log.info("【按类型查询商户】无坐标参数，按类型分页查询");
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            log.info("【按类型查询商户】查询到 {} 条记录", page.getRecords().size());
            return Result.ok(page.getRecords());
        }

        // 有坐标，按距离排序（附近商户功能）
        log.info("【按类型查询商户】有坐标参数，按距离排序查询附近商户");
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        if (results == null) {
            log.info("【按类型查询商户】附近没有找到商户");
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            log.info("【按类型查询商户】当前页无数据");
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        log.info("【按类型查询商户】查询到 {} 条附近商户", shops.size());
        return Result.ok(shops);
    }
}
