package com.mrp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Production queue polling belongs to the worker; local/test profiles keep embedded scheduling. */
@Configuration
@Profile({"worker", "!prod"})
@EnableScheduling
public class WorkerSchedulingConfig {
}
