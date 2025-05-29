package com.yxc.thumbbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.yxc.thumbbackend.mapper")
@EnableScheduling
public class ThumbBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThumbBackendApplication.class, args);
    }

}
