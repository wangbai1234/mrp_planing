package com.mrp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {JdbcTemplateAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@EnableAsync
public class MrpApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MrpApiApplication.class, args);
    }
}
