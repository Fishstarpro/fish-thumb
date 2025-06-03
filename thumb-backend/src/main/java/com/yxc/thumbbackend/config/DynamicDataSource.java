package com.yxc.thumbbackend.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 动态数据源路由
 * 实现读写分离和故障转移逻辑
 */
@Slf4j
public class DynamicDataSource extends AbstractRoutingDataSource {

    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    @Override
    protected Object determineCurrentLookupKey() {
        String operationType = DataSourceContextHolder.getDataSourceType();
        
        if ("master".equals(operationType) || operationType == null) {
            log.debug("Routing to master database");
            return "master";
        }
        
        // 读操作路由到从库，使用负载均衡
        return getAvailableSlave();
    }

    /**
     * 获取可用的从库（带负载均衡和故障转移）
     */
    private String getAvailableSlave() {
        // 简单的轮询负载均衡
        int slaveCount = getSlaveCount();
        if (slaveCount == 0) {
            log.warn("No slave databases available, falling back to master");
            return "master";
        }
        
        int index = roundRobinCounter.getAndIncrement() % slaveCount;
        String slaveKey = "slave-" + (index + 1);
        
        // 检查从库是否可用
        if (isDataSourceAvailable(slaveKey)) {
            log.debug("Routing to slave database: {}", slaveKey);
            return slaveKey;
        }
        
        // 如果选中的从库不可用，尝试其他从库
        for (int i = 0; i < slaveCount; i++) {
            String alternativeKey = "slave-" + (i + 1);
            if (!alternativeKey.equals(slaveKey) && isDataSourceAvailable(alternativeKey)) {
                log.debug("Routing to alternative slave database: {}", alternativeKey);
                return alternativeKey;
            }
        }
        
        // 所有从库都不可用，回退到主库
        log.warn("All slave databases unavailable, falling back to master");
        return "master";
    }

    /**
     * 检查数据源是否可用
     */
    private boolean isDataSourceAvailable(String dataSourceKey) {
        try {
            DataSource dataSource = (DataSource) getResolvedDataSources().get(dataSourceKey);
            if (dataSource == null) {
                return false;
            }
            
            try (Connection connection = dataSource.getConnection()) {
                return connection.isValid(5); // 5秒超时
            }
        } catch (SQLException e) {
            log.warn("Database {} is not available: {}", dataSourceKey, e.getMessage());
            return false;
        }
    }

    /**
     * 获取从库数量（这里简化处理，实际应该从配置中获取）
     */
    private int getSlaveCount() {
        return (int) getResolvedDataSources().keySet().stream()
                .filter(key -> key.toString().startsWith("slave-"))
                .count();
    }
} 