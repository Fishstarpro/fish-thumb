package com.yxc.thumbbackend.config;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.Charset;

/**
 * 布隆过滤器配置类
 * 用于优化点赞查询性能，减少缓存穿透
 */
@Configuration
@Slf4j
public class BloomFilterConfig {

    /**
     * 创建点赞记录布隆过滤器
     * 预期插入100万条记录，误判率0.01%
     */
    @Bean
    public BloomFilter<String> thumbBloomFilter() {
        return BloomFilter.create(
                Funnels.stringFunnel(Charset.defaultCharset()),
                1000000, // 预期插入的数据量
                0.0001   // 误判率
        );
    }
} 