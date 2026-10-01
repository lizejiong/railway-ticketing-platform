package com.lzj.railway.ticket;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 票务域服务启动入口。
 */
@MapperScan("com.lzj.railway.ticket.dao.mapper")
@SpringBootApplication
public class TicketServiceApplication {

    /**
     * 启动票务服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }
}
