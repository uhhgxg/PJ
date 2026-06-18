package com.merchant.review.controller;


import com.merchant.review.dto.Result;
import com.merchant.review.entity.Voucher;
import com.merchant.review.service.IVoucherService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@Slf4j
@Tag(name = "优惠券")
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通券
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        log.info("【请求】新增普通优惠券，名称：{}", voucher.getTitle());
        voucherService.save(voucher);
        log.info("【响应】新增成功，优惠券ID：{}", voucher.getId());
        return Result.ok(voucher.getId());
    }

    /**
     * 新增秒杀券
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        log.info("【请求】新增秒杀优惠券，名称：{}，库存：{}", voucher.getTitle(), voucher.getStock());
        voucherService.addSeckillVoucher(voucher);
        log.info("【响应】秒杀券新增成功，优惠券ID：{}", voucher.getId());
        return Result.ok(voucher.getId());
    }

    /**
     * 查询店铺的优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        log.info("【请求】查询店铺优惠券，商铺ID：{}", shopId);
        return voucherService.queryVoucherOfShop(shopId);
    }
}
