package com.mrp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {JdbcTemplateAutoConfiguration.class})
@EnableAsync
@EnableScheduling
public class MrpApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MrpApiApplication.class, args);
    }
}
