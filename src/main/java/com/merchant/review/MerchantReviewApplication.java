package com.merchant.review;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.merchant.review.mapper")
@SpringBootApplication
public class MerchantReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantReviewApplication.class, args);
    }

}
