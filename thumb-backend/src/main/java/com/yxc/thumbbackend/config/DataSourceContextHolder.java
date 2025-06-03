package com.yxc.thumbbackend.config;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据源上下文持有者
 * 使用ThreadLocal实现线程安全的数据源类型管理
 */
@Slf4j
public class DataSourceContextHolder {

    /**
     * 主库标识
     */
    public static final String MASTER = "master";
    
    /**
     * 从库标识
     */
    public static final String SLAVE = "slave";

    /**
     * 线程本地变量，存储当前线程的数据源类型
     */
    private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置数据源类型
     * @param dataSourceType 数据源类型
     */
    public static void setDataSourceType(String dataSourceType) {
        CONTEXT_HOLDER.set(dataSourceType);
        log.debug("Set data source type to: {}", dataSourceType);
    }

    /**
     * 获取数据源类型
     * @return 数据源类型
     */
    public static String getDataSourceType() {
        String dataSourceType = CONTEXT_HOLDER.get();
        log.debug("Get data source type: {}", dataSourceType);
        return dataSourceType;
    }

    /**
     * 清除数据源类型
     */
    public static void clearDataSourceType() {
        CONTEXT_HOLDER.remove();
        log.debug("Cleared data source type");
    }

    /**
     * 设置为主库
     */
    public static void useMaster() {
        setDataSourceType(MASTER);
    }

    /**
     * 设置为从库
     */
    public static void useSlave() {
        setDataSourceType(SLAVE);
    }
} 