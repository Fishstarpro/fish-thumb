package com.yxc.thumbbackend.config;

import com.yxc.thumbbackend.service.ThumbService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 布隆过滤器初始化器
 * 在应用启动完成后初始化布隆过滤器
 */
@Component
@Slf4j
public class BloomFilterInitializer implements ApplicationRunner {

    @Resource
    private ThumbService thumbService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            thumbService.initBloomFilter();
            log.info("点赞记录布隆过滤器初始化完成");
        } catch (Exception e) {
            log.error("初始化点赞记录布隆过滤器失败", e);
        }
    }
} 