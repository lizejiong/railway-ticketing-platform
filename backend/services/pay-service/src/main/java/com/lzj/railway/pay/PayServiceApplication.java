package com.lzj.railway.pay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** 支付服务启动入口。 */
@SpringBootApplication
@MapperScan("com.lzj.railway.pay.dao.mapper")
@EnableFeignClients(basePackages = "com.lzj.railway.pay.remote")
public class PayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayServiceApplication.class, args);
    }
}
