package com.yxc.thumbbackend.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置类
 * 
 * @author fishstar
 */
@Configuration
public class RedissonConfig {

    @Value("${redisson.address:redis://localhost:6379}")
    private String address;

    @Value("${redisson.password:}")
    private String password;

    @Value("${redisson.database:0}")
    private int database;

    @Value("${redisson.timeout:3000}")
    private int timeout;

    @Value("${redisson.connection-pool-size:64}")
    private int connectionPoolSize;

    @Value("${redisson.connection-minimum-idle-size:10}")
    private int connectionMinimumIdleSize;

    @Value("${redisson.idle-connection-timeout:10000}")
    private int idleConnectionTimeout;

    @Value("${redisson.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${redisson.retry-attempts:3}")
    private int retryAttempts;

    @Value("${redisson.retry-interval:1500}")
    private int retryInterval;

    /**
     * 配置Redisson客户端
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        // 单机模式配置
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setTimeout(timeout)
                .setConnectionPoolSize(connectionPoolSize)
                .setConnectionMinimumIdleSize(connectionMinimumIdleSize)
                .setIdleConnectionTimeout(idleConnectionTimeout)
                .setConnectTimeout(connectTimeout)
                .setRetryAttempts(retryAttempts)
                .setRetryInterval(retryInterval);

        // 如果有密码则设置密码
        if (password != null && !password.trim().isEmpty()) {
            config.useSingleServer().setPassword(password);
        }

        return Redisson.create(config);
    }
} 