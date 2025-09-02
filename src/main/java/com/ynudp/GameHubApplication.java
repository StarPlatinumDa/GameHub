package com.ynudp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.ynudp.mapper")
@SpringBootApplication
public class GameHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameHubApplication.class, args);
    }

}
