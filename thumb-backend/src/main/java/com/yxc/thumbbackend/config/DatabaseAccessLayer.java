package com.yxc.thumbbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据库访问层
 * 实现读写分离和故障转移逻辑，类似TiDB的高可用方案
 */
@Component
@Slf4j
public class DatabaseAccessLayer {

    @Autowired
    private DataSource masterDataSource;
    
    @Autowired
    private List<DataSource> slaveDataSources;

    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    /**
     * 操作类型枚举
     */
    public enum OperationType {
        READ, WRITE
    }

    /**
     * 获取数据库连接
     * @param type 操作类型
     * @return 数据库连接
     */
    public Connection getConnection(OperationType type) {
        if (type == OperationType.WRITE) {
            return getAvailableMaster();
        } else {
            return getAvailableReplica();
        }
    }

    /**
     * 获取主库连接
     * @return 主库连接
     */
    private Connection getAvailableMaster() {
        try {
            Connection conn = masterDataSource.getConnection();
            if (isAvailable(conn)) {
                log.debug("Connected to master database");
                return conn;
            }
        } catch (SQLException e) {
            log.error("Failed to connect to master database: {}", e.getMessage());
        }
        
        // 主库不可用时的紧急写入策略
        return emergencyWritePolicy();
    }

    /**
     * 获取从库连接（负载均衡）
     * @return 从库连接
     */
    private Connection getAvailableReplica() {
        // 如果没有从库配置，使用主库
        if (slaveDataSources == null || slaveDataSources.isEmpty()) {
            log.debug("No slave databases configured, using master for read operations");
            return getAvailableMaster();
        }

        // 轮询选择从库
        for (int i = 0; i < slaveDataSources.size(); i++) {
            int index = roundRobinCounter.getAndIncrement() % slaveDataSources.size();
            DataSource slaveDataSource = slaveDataSources.get(index);
            
            try {
                Connection conn = slaveDataSource.getConnection();
                if (isAvailable(conn)) {
                    log.debug("Connected to slave database: slave-{}", index + 1);
                    return conn;
                }
            } catch (SQLException e) {
                log.warn("Failed to connect to slave database slave-{}: {}", index + 1, e.getMessage());
            }
        }

        // 所有从库都不可用，回退到主库
        log.warn("All slave databases unavailable, falling back to master for read operations");
        return getAvailableMaster();
    }

    /**
     * 检查连接是否可用
     * @param conn 数据库连接
     * @return 是否可用
     */
    private boolean isAvailable(Connection conn) {
        try {
            return conn != null && conn.isValid(5); // 5秒超时
        } catch (SQLException e) {
            log.warn("Connection validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 紧急写入策略
     * 当主库不可用时的处理策略
     * @return 可用的连接
     */
    private Connection emergencyWritePolicy() {
        log.error("Master database is unavailable, emergency write policy activated");
        
        // 在生产环境中，这里可能需要：
        // 1. 触发告警
        // 2. 尝试连接备用主库
        // 3. 如果配置了读写分离，可能需要临时使用从库（但需要注意数据一致性）
        
        // 这里简化处理，返回null并让上层处理
        throw new RuntimeException("数据库主库不可用，无法执行写操作");
    }

    /**
     * 获取数据库连接（自动判断读写类型）
     * 根据SQL语句类型自动选择数据源
     * @param sql SQL语句
     * @return 数据库连接
     */
    public Connection getConnection(String sql) {
        if (sql == null) {
            return getConnection(OperationType.READ);
        }
        
        String upperSql = sql.trim().toUpperCase();
        if (upperSql.startsWith("SELECT") || 
            upperSql.startsWith("SHOW") || 
            upperSql.startsWith("EXPLAIN") ||
            upperSql.startsWith("DESC")) {
            return getConnection(OperationType.READ);
        } else {
            return getConnection(OperationType.WRITE);
        }
    }

    /**
     * 手动设置数据源类型
     * @param usemaster 是否使用主库
     */
    public void setDataSourceType(boolean usemaster) {
        if (usemaster) {
            DataSourceContextHolder.useMaster();
        } else {
            DataSourceContextHolder.useSlave();
        }
    }

    /**
     * 清理数据源上下文
     */
    public void clearDataSourceType() {
        DataSourceContextHolder.clearDataSourceType();
    }
} 