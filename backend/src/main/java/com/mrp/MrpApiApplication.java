package com.mrp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;

@SpringBootApplication(exclude = {JdbcTemplateAutoConfiguration.class})
public class MrpApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MrpApiApplication.class, args);
    }
}
