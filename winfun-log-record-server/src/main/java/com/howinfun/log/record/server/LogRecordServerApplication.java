package com.howinfun.log.record.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * application
 * @author winfun
 * @date 2021/2/26 2:51 下午
 **/
@MapperScan("com.howinfun.log.record.server.mapper")
@SpringBootApplication
public class LogRecordServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogRecordServerApplication.class, args);
    }
}
