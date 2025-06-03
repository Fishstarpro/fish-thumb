package com.yxc.thumbbackend.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariDataSource;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据库高可用配置
 * 实现读写分离和故障转移
 */
@Configuration
@ConfigurationProperties(prefix = "spring.datasource")
@EnableConfigurationProperties
@Data
@Slf4j
public class DataSourceConfig {

    private Master master = new Master();
    private Slaves slaves = new Slaves();

    @Data
    public static class Master {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
    }

    @Data
    public static class Slaves {
        private List<String> urls = new ArrayList<>();
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
    }

    /**
     * 主数据源配置
     */
    @Bean
    @Primary
    public DataSource masterDataSource() {
        return createDataSource(master.getUrl(), master.getUsername(), master.getPassword(), "master");
    }

    /**
     * 从数据源列表配置
     */
    @Bean
    public List<DataSource> slaveDataSources() {
        List<DataSource> slavesDataSources = new ArrayList<>();
        for (int i = 0; i < slaves.getUrls().size(); i++) {
            slavesDataSources.add(createDataSource(
                slaves.getUrls().get(i), 
                slaves.getUsername(), 
                slaves.getPassword(), 
                "slave-" + (i + 1)
            ));
        }
        return slavesDataSources;
    }

    /**
     * 动态数据源配置
     */
    @Bean
    public DynamicDataSource dynamicDataSource() {
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        
        // 主数据源
        DataSource masterDs = masterDataSource();
        targetDataSources.put("master", masterDs);
        
        // 从数据源
        List<DataSource> slavesDs = slaveDataSources();
        for (int i = 0; i < slavesDs.size(); i++) {
            targetDataSources.put("slave-" + (i + 1), slavesDs.get(i));
        }
        
        dynamicDataSource.setTargetDataSources(targetDataSources);
        dynamicDataSource.setDefaultTargetDataSource(masterDs);
        
        return dynamicDataSource;
    }

    /**
     * 创建数据源
     */
    private DataSource createDataSource(String url, String username, String password, String name) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        
        // 连接池配置
        dataSource.setMaximumPoolSize(20);
        dataSource.setMinimumIdle(5);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        dataSource.setLeakDetectionThreshold(60000);
        
        // 健康检查
        dataSource.setConnectionTestQuery("SELECT 1");
        dataSource.setValidationTimeout(5000);
        
        // 连接池名称
        dataSource.setPoolName(name + "-pool");
        
        log.info("Created data source: {} with URL: {}", name, url);
        return dataSource;
    }
} 