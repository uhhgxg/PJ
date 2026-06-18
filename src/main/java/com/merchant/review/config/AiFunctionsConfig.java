package com.merchant.review.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.merchant.review.entity.Shop;
import com.merchant.review.entity.ShopType;
import com.merchant.review.entity.Voucher;
import com.merchant.review.entity.SeckillVoucher;
import com.merchant.review.service.ISeckillVoucherService;
import com.merchant.review.service.IShopService;
import com.merchant.review.service.IShopTypeService;
import com.merchant.review.service.IVoucherService;
import com.merchant.review.service.ai.AiToolFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.merchant.review.utils.RedisConstants.SHOP_GEO_KEY;

@Slf4j
@Configuration
public class AiFunctionsConfig {

    @Resource
    private IShopTypeService shopTypeService;

    @Resource
    private IShopService shopService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private IVoucherService voucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String EMPTY_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private AiToolFunction func(String name, String description, String jsonSchema,
                                java.util.function.Function<String, String> handler) {
        return new AiToolFunction(name, description, jsonSchema, handler);
    }

    @Bean
    public List<AiToolFunction> aiToolFunctions() {
        List<AiToolFunction> tools = new ArrayList<>();

        // 1. 获取商铺分类
        tools.add(func("getShopTypes", "获取所有商铺分类列表，例如：美食、KTV、酒吧等",
                EMPTY_SCHEMA,
                input -> {
                    List<ShopType> list = shopTypeService.query().orderByAsc("sort").list();
                    return JSONUtil.toJsonStr(list == null ? Collections.emptyList() : list);
                }));

        // 2. 按关键词搜索商铺
        tools.add(func("searchShops", "根据商铺名称关键词搜索商铺，返回匹配的商铺列表",
                "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\",\"description\":\"搜索关键词\"}},\"required\":[\"keyword\"]}",
                input -> {
                    String keyword = JSONUtil.parseObj(input).getStr("keyword");
                    if (StrUtil.isBlank(keyword)) return "[]";
                    List<Shop> list = shopService.query()
                            .like("name", keyword).last("LIMIT 20").list();
                    return JSONUtil.toJsonStr(list == null ? Collections.emptyList() : list);
                }));

        // 3. 获取商铺详情
        tools.add(func("getShopDetail", "根据商铺ID获取商铺详细信息，包括评分、均价、地址、营业时间等",
                "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"number\",\"description\":\"商铺ID\"}},\"required\":[\"id\"]}",
                input -> {
                    Long id = JSONUtil.parseObj(input).getLong("id");
                    if (id == null) return "{}";
                    Shop shop = shopService.getById(id);
                    return shop == null ? "{}" : JSONUtil.toJsonStr(shop);
                }));

        // 4. 查找附近商铺
        tools.add(func("getNearbyShops", "根据商铺分类名称和用户坐标，查找附近5000米内的商铺",
                "{\"type\":\"object\",\"properties\":{" +
                        "\"typeName\":{\"type\":\"string\",\"description\":\"商铺分类名称，如：美食、KTV、酒吧\"}," +
                        "\"x\":{\"type\":\"number\",\"description\":\"经度\"}," +
                        "\"y\":{\"type\":\"number\",\"description\":\"纬度\"}" +
                        "},\"required\":[\"typeName\",\"x\",\"y\"]}",
                input -> {
                    var obj = JSONUtil.parseObj(input);
                    String typeName = obj.getStr("typeName");
                    Double x = obj.getDouble("x");
                    Double y = obj.getDouble("y");

                    if (StrUtil.isBlank(typeName) || x == null || y == null) {
                        return "{\"error\":\"缺少必要参数\"}";
                    }

                    ShopType type = shopTypeService.query().eq("name", typeName).one();
                    if (type == null) {
                        List<ShopType> allTypes = shopTypeService.query().orderByAsc("sort").list();
                        String names = allTypes.stream().map(ShopType::getName).collect(Collectors.joining("、"));
                        return "{\"error\":\"未找到分类【" + typeName + "】，可用分类：" + names + "\"}";
                    }

                    String key = SHOP_GEO_KEY + type.getId();
                    GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                            .search(key, GeoReference.fromCoordinate(x, y),
                                    new Distance(5000),
                                    RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                            .includeDistance().limit(20));

                    if (results == null || results.getContent().isEmpty()) {
                        return "{\"message\":\"附近没有找到【" + typeName + "】类商铺\"}";
                    }

                    List<Map<String, Object>> shopList = new ArrayList<>();
                    for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
                        Long shopId = Long.valueOf(result.getContent().getName());
                        double distance = result.getDistance().getValue();
                        Shop shop = shopService.getById(shopId);
                        if (shop != null) {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("name", shop.getName());
                            item.put("address", shop.getAddress());
                            item.put("avgPrice", shop.getAvgPrice());
                            item.put("score", shop.getScore() != null ? shop.getScore() / 10.0 : null);
                            item.put("distance", String.format("%.0f米", distance));
                            shopList.add(item);
                        }
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("total", shopList.size());
                    result.put("shops", shopList);
                    return JSONUtil.toJsonStr(result);
                }));

        // 5. 查询商铺优惠券
        tools.add(func("getShopVouchers", "获取指定商铺的所有可用优惠券/代金券信息，传入商铺ID",
                "{\"type\":\"object\",\"properties\":{\"shopId\":{\"type\":\"number\",\"description\":\"商铺ID\"}},\"required\":[\"shopId\"]}",
                input -> {
                    Long shopId = JSONUtil.parseObj(input).getLong("shopId");
                    if (shopId == null) return "[]";
                    List<Voucher> vouchers = voucherService.query().eq("shop_id", shopId).list();
                    if (vouchers == null || vouchers.isEmpty()) return "[]";

                    List<Map<String, Object>> result = new ArrayList<>();
                    for (Voucher v : vouchers) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("title", v.getTitle());
                        item.put("subTitle", v.getSubTitle());
                        item.put("payValue", v.getPayValue());
                        item.put("actualValue", v.getActualValue());
                        item.put("type", v.getType() == 1 ? "秒杀券" : "普通券");
                        result.add(item);
                    }
                    return JSONUtil.toJsonStr(result);
                }));

        // 6. 查询秒杀活动
        tools.add(func("getActiveSeckillVouchers", "获取当前正在进行中的秒杀优惠券活动列表",
                EMPTY_SCHEMA,
                input -> {
                    LocalDateTime now = LocalDateTime.now();
                    List<SeckillVoucher> seckillList = seckillVoucherService.query()
                            .le("begin_time", now).gt("end_time", now).list();
                    if (seckillList == null || seckillList.isEmpty()) return "[]";

                    List<Map<String, Object>> result = new ArrayList<>();
                    for (SeckillVoucher sv : seckillList) {
                        Voucher voucher = voucherService.getById(sv.getVoucherId());
                        if (voucher == null) continue;
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("title", voucher.getTitle());
                        item.put("stock", sv.getStock());
                        item.put("payValue", voucher.getPayValue());
                        item.put("actualValue", voucher.getActualValue());
                        item.put("beginTime", sv.getBeginTime().toString());
                        item.put("endTime", sv.getEndTime().toString());
                        result.add(item);
                    }
                    return JSONUtil.toJsonStr(result);
                }));

        return tools;
    }
}
