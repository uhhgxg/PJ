package com.merchant.review.controller;


import com.merchant.review.dto.Result;
import com.merchant.review.service.IVoucherOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

@Slf4j
@Tag(name = "秒杀订单")
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        log.info("【请求】秒杀下单，优惠券ID：{}", voucherId);
        Result result = voucherOrderService.seckillVoucher(voucherId);
        log.info("【响应】秒杀结果：{}", result);
        return result;
    }
}
