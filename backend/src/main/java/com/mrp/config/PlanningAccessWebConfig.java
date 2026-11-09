package com.mrp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PlanningAccessWebConfig implements WebMvcConfigurer {
    private final PlanningFactoryInterceptor interceptor;
    public PlanningAccessWebConfig(PlanningFactoryInterceptor interceptor) { this.interceptor = interceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/mrp/**");
    }
}
