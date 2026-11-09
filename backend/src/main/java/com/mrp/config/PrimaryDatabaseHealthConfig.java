package com.mrp.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API readiness depends on the local MRP database; Oracle is used only for explicit BOM sync. */
@Configuration
public class PrimaryDatabaseHealthConfig {

    @Bean(name = "dbHealthIndicator")
    public HealthIndicator dbHealthIndicator(@Qualifier("dataSource") DataSource primaryDataSource) {
        return new DataSourceHealthIndicator(primaryDataSource);
    }
}
