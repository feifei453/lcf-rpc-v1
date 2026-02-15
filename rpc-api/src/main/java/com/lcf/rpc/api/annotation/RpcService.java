package com.lcf.rpc.api.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE) // 作用于类
@Retention(RetentionPolicy.RUNTIME)
@Component // 继承 @Component，让 Spring 能扫描到它
public @interface RpcService {
    // 显式指定服务接口 (可选，如果不指定则取第一个接口)
    Class<?> interfaceClass() default void.class;
}