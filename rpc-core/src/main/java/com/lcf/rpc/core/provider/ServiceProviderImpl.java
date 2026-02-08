package com.lcf.rpc.core.provider;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的服务提供者（服务端本地注册表）
 * 作用：保存 服务名 -> 服务实现类 的映射关系
 */
@Slf4j
public class ServiceProviderImpl {

    /**
     * 核心容器：Key = 接口全限定名, Value = 实现类对象
     */
    private static final Map<String, Object> serviceMap = new ConcurrentHashMap<>();

    /**
     * 用来记录已经注册的服务，防止重复注册
     */
    private static final Set<String> registeredService = ConcurrentHashMap.newKeySet();

    /**
     * 注册服务
     * @param service 服务实现类对象 (如 new HelloServiceImpl())
     */
    public <T> void addServiceProvider(T service) {
        // 1. 获取该实现类实现的所有接口
        // (通常一个实现类实现一个接口，但也可以实现多个)
        String serviceName = service.getClass().getInterfaces()[0].getCanonicalName();

        if (registeredService.contains(serviceName)) {
            return; // 已注册过，直接返回
        }

        registeredService.add(serviceName);
        serviceMap.put(serviceName, service);
        log.info("向本地注册表中注册服务: {} >>> {}", serviceName, service.getClass().getCanonicalName());
    }

    /**
     * 获取服务
     * @param serviceName 接口名
     * @return 服务实现类对象
     */
    public Object getServiceProvider(String serviceName) {
        Object service = serviceMap.get(serviceName);
        if (service == null) {
            throw new RuntimeException("服务未找到: " + serviceName);
        }
        return service;
    }
}