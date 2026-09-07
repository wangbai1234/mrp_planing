package com.mrp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MrpWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MrpWorkerApplication.class, args);
    }
}
