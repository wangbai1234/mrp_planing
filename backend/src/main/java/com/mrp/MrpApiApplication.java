package com.mrp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {JdbcTemplateAutoConfiguration.class})
@EnableAsync
public class MrpApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MrpApiApplication.class, args);
    }
}
