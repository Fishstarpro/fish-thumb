package com.yxc.thumbbackend.annotation;

import java.lang.annotation.*;

/**
 * 只读注解
 * 标记在方法上，表示该方法只进行读操作，应该使用从库
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReadOnly {
    
    /**
     * 是否强制使用主库
     * @return true-强制使用主库，false-使用从库（默认）
     */
    boolean forceMaster() default false;
} 