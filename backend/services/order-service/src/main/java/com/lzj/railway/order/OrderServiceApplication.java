package com.lzj.railway.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 订单域服务启动入口。 */
@MapperScan("com.lzj.railway.order.dao.mapper")
@SpringBootApplication
public class OrderServiceApplication {

    /** 启动订单服务。 */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
