package com.mrp.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "com.mrp.bom.repository", sqlSessionFactoryRef = "oracleSqlSessionFactory")
public class OracleDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(OracleDataSourceConfig.class);

    @Bean(name = "oracleDataSource")
    public DataSource oracleDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("oracle.jdbc.OracleDriver");
        dataSource.setJdbcUrl("jdbc:oracle:thin:@172.17.210.203:1521:ORCL");
        dataSource.setUsername("QSM");
        dataSource.setPassword("QSM");
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(2);
        dataSource.setPoolName("OracleHikariPool");
        dataSource.setInitializationFailTimeout(0);
        dataSource.setConnectionTimeout(30000);
        dataSource.setValidationTimeout(5000);
        log.info("Oracle数据源已配置: 172.17.210.203:1521:ORCL");
        return dataSource;
    }

    @Bean(name = "oracleSqlSessionFactory")
    public SqlSessionFactory oracleSqlSessionFactory(@Qualifier("oracleDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/bom/BomMapper.xml"));
        SqlSessionFactory sessionFactory = factoryBean.getObject();
        sessionFactory.getConfiguration().setMapUnderscoreToCamelCase(true);
        return sessionFactory;
    }

    @Bean(name = "oracleSqlSessionTemplate")
    public SqlSessionTemplate oracleSqlSessionTemplate(@Qualifier("oracleSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "oracleTransactionManager")
    public PlatformTransactionManager oracleTransactionManager(@Qualifier("oracleDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
