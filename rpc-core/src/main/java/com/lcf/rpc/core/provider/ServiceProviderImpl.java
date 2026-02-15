package com.lcf.rpc.core.provider;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ServiceProviderImpl implements ServiceProvider {

    /**
     * 核心存储容器：Key = 接口全限定名, Value = 服务实现类实例
     * 使用 static 保证全局唯一，无论 new 多少个 ServiceProviderImpl，数据都是共享的
     */
    private static final Map<String, Object> serviceMap = new ConcurrentHashMap<>();

    @Override
    public void addServiceProvider(Object service, String serviceName) {
        if (serviceMap.containsKey(serviceName)) {
            return;
        }
        serviceMap.put(serviceName, service);
        log.info("向本地注册表注册服务: {} >>> {}", serviceName, service.getClass().getName());
    }

    @Override
    public Object getServiceProvider(String serviceName) {
        Object service = serviceMap.get(serviceName);
        if (service == null) {
            throw new RuntimeException("未找到服务: " + serviceName + "，请检查是否已添加 @RpcService 注解");
        }
        return service;
    }
}