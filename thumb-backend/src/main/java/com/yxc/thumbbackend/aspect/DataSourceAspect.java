package com.yxc.thumbbackend.aspect;

import com.yxc.thumbbackend.annotation.ReadOnly;
import com.yxc.thumbbackend.config.DataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 数据源切面
 * 自动处理读写分离逻辑
 */
@Aspect
@Component
@Order(1) // 确保在事务切面之前执行
@Slf4j
public class DataSourceAspect {

    /**
     * 切点：拦截所有Service层的方法
     */
    @Pointcut("execution(* com.yxc.thumbbackend.service..*.*(..))")
    public void servicePointcut() {}

    /**
     * 切点：拦截所有带@ReadOnly注解的方法
     */
    @Pointcut("@annotation(com.yxc.thumbbackend.annotation.ReadOnly)")
    public void readOnlyPointcut() {}

    /**
     * 环绕通知：处理数据源路由
     */
    @Around("servicePointcut() || readOnlyPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // 获取方法和类上的@ReadOnly注解
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            ReadOnly readOnlyAnnotation = getReadOnlyAnnotation(method, joinPoint.getTarget().getClass());

            // 设置数据源
            setDataSource(method, readOnlyAnnotation);

            // 执行方法
            return joinPoint.proceed();
        } finally {
            // 清理数据源上下文
            DataSourceContextHolder.clearDataSourceType();
        }
    }

    /**
     * 获取@ReadOnly注解
     */
    private ReadOnly getReadOnlyAnnotation(Method method, Class<?> targetClass) {
        // 先检查方法上的注解
        ReadOnly readOnly = method.getAnnotation(ReadOnly.class);
        if (readOnly != null) {
            return readOnly;
        }
        
        // 再检查类上的注解
        return targetClass.getAnnotation(ReadOnly.class);
    }

    /**
     * 设置数据源
     */
    private void setDataSource(Method method, ReadOnly readOnlyAnnotation) {
        String methodName = method.getName();
        
        if (readOnlyAnnotation != null) {
            // 有@ReadOnly注解
            if (readOnlyAnnotation.forceMaster()) {
                log.debug("Method {} marked as @ReadOnly(forceMaster=true), using master", methodName);
                DataSourceContextHolder.useMaster();
            } else {
                log.debug("Method {} marked as @ReadOnly, using slave", methodName);
                DataSourceContextHolder.useSlave();
            }
        } else {
            // 没有@ReadOnly注解，根据方法名判断
            if (isReadOnlyMethod(methodName)) {
                log.debug("Method {} detected as read-only, using slave", methodName);
                DataSourceContextHolder.useSlave();
            } else {
                log.debug("Method {} detected as write operation, using master", methodName);
                DataSourceContextHolder.useMaster();
            }
        }
    }

    /**
     * 根据方法名判断是否为只读方法
     */
    private boolean isReadOnlyMethod(String methodName) {
        return methodName.startsWith("get") ||
               methodName.startsWith("find") ||
               methodName.startsWith("query") ||
               methodName.startsWith("search") ||
               methodName.startsWith("count") ||
               methodName.startsWith("list") ||
               methodName.startsWith("page") ||
               methodName.startsWith("select") ||
               methodName.startsWith("check") ||
               methodName.startsWith("exist") ||
               methodName.startsWith("is");
    }
} 