package com.merchant.review.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.merchant.review.dto.Result;
import com.merchant.review.entity.Shop;
import com.merchant.review.service.IShopService;
import com.merchant.review.utils.SystemConstants;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@Slf4j
@Tag(name = "商铺")
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        log.info("【请求】查询商铺详情，ID：{}", id);
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        log.info("【请求】新增商铺，名称：{}，类型ID：{}", shop.getName(), shop.getTypeId());
        shopService.save(shop);
        log.info("【响应】新增成功，商铺ID：{}", shop.getId());
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        log.info("【请求】更新商铺，ID：{}，名称：{}", shop.getId(), shop.getName());
        return shopService.update(shop);
    }

    /**
     * 根据商铺类型分页查询
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        log.info("【请求】按类型查询商铺，类型：{}，第{}页，坐标：({}, {})", typeId, current, x, y);
        return shopService.queryShopByType(typeId, current, x, y);
    }

    /**
     * 根据名称关键字分页查询
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        log.info("【请求】按名称搜索商铺，关键字：{}，第{}页", name, current);
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        log.info("【响应】搜索到 {} 条商铺", page.getRecords().size());
        return Result.ok(page.getRecords());
    }
}
