package com.minipay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MiniPayApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniPayApplication.class, args);
    }
}
