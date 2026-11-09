package com.mrp.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class SchedulingConfig {
    // Defining only the heartbeat scheduler would disable Boot's default scheduler
    // and make the synchronous polling method share the heartbeat thread again.
    @Bean
    @Primary
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    @Bean
    public ThreadPoolTaskScheduler mrpHeartbeatScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.poolSize(1).threadNamePrefix("mrp-heartbeat-").build();
    }

    @Bean
    public ThreadPoolTaskScheduler mrpLifecycleScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.poolSize(1).threadNamePrefix("mrp-lifecycle-").build();
    }

    @Bean
    public ThreadPoolTaskScheduler bomSyncScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.poolSize(1).threadNamePrefix("bom-sync-").build();
    }
}
