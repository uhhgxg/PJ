package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private RedisUtils redisUtils;

    private static final String SHOP_TYPE_LIST_KEY = CACHE_SHOP_TYPE_KEY + "list";

    @Override
    public Result queryList() {
        List<ShopType> list = redisUtils.getList(SHOP_TYPE_LIST_KEY, ShopType.class);
        if (list != null && !list.isEmpty()) {
            return Result.ok(list);
        }
        list = query().orderByAsc("sort").list();
        if (list == null || list.isEmpty()) {
            return Result.fail("店铺类型列表不存在！");
        }
        redisUtils.set(SHOP_TYPE_LIST_KEY, list, CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(list);
    }
}
