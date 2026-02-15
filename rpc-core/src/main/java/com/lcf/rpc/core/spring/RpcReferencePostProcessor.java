package com.lcf.rpc.core.spring;

import com.lcf.rpc.core.annotation.RpcReference;
import com.lcf.rpc.core.proxy.RpcClientProxy;
import com.lcf.rpc.core.transport.NettyClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Slf4j
@Component
public class RpcReferencePostProcessor implements BeanPostProcessor {

    private final RpcClientProxy rpcClientProxy;

    public RpcReferencePostProcessor() {
        // 全局单例的 NettyClient 和 Proxy 工厂
        this.rpcClientProxy = new RpcClientProxy(new NettyClient());
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        // 遍历所有字段
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(RpcReference.class)) {
                // 找到 @RpcReference 注解的字段
                RpcReference rpcReference = field.getAnnotation(RpcReference.class);
                Class<?> interfaceClass = field.getType();

                // 1. 创建代理对象
                Object proxy = rpcClientProxy.getProxy(interfaceClass);

                // 2. 暴力反射注入
                field.setAccessible(true);
                try {
                    field.set(bean, proxy);
                    log.info("Spring 注入代理对象: {} -> {}", beanName, interfaceClass.getName());
                } catch (IllegalAccessException e) {
                    log.error("注入失败", e);
                }
            }
        }
        return bean;
    }
}