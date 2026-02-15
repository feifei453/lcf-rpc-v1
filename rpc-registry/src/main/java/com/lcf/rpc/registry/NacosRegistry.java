package com.lcf.rpc.registry;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.lcf.rpc.common.config.RpcProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class NacosRegistry implements Registry {

    private final NamingService namingService;

    // 1. 本地缓存: serviceName -> list of addresses
    private final Map<String, List<InetSocketAddress>> serviceCache = new ConcurrentHashMap<>();

    // 2. 记录已订阅的服务，防止重复订阅
    private final Set<String> subscribingServices = new HashSet<>();

    public NacosRegistry() {
        try {
            // 3. 去硬编码：从配置文件读取 Nacos 地址
            String registryAddress = RpcProperties.getRegistryAddress();
            // Nacos 工厂创建连接
            this.namingService = NamingFactory.createNamingService(registryAddress);
            log.info("Nacos 连接成功: {}", registryAddress);
        } catch (NacosException e) {
            throw new RuntimeException("连接 Nacos 失败", e);
        }
    }

    @Override
    public void register(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            namingService.registerInstance(serviceName, inetSocketAddress.getHostName(), inetSocketAddress.getPort());
            log.info("Nacos 注册服务成功: {} -> {}", serviceName, inetSocketAddress);
        } catch (NacosException e) {
            throw new RuntimeException("Nacos 注册失败", e);
        }
    }

    @Override
    public List<InetSocketAddress> lookupAll(String serviceName) {
        // 4. 优先读本地缓存 (性能起飞)
        if (serviceCache.containsKey(serviceName)) {
            return serviceCache.get(serviceName);
        }

        try {
            // 5. 第一次查询：从 Nacos 拉取，并更新缓存
            List<Instance> instances = namingService.selectInstances(serviceName, true);
            List<InetSocketAddress> addressList = instancesToAddressList(instances);

            serviceCache.put(serviceName, addressList);

            // 6. 开启订阅 (监听服务变化)
            subscribeService(serviceName);

            return addressList;
        } catch (NacosException e) {
            throw new RuntimeException("Nacos 服务发现失败", e);
        }
    }

    private void subscribeService(String serviceName) throws NacosException {
        // 只有没订阅过才订阅，加锁防止并发重复订阅
        synchronized (subscribingServices) {
            if (subscribingServices.contains(serviceName)) {
                return;
            }

            log.info("开启 Nacos 监听: {}", serviceName);

            // Nacos 的事件监听机制
            namingService.subscribe(serviceName, new EventListener() {
                @Override
                public void onEvent(Event event) {
                    if (event instanceof NamingEvent) {
                        NamingEvent namingEvent = (NamingEvent) event;
                        // 获取最新的实例列表
                        List<Instance> instances = namingEvent.getInstances();
                        List<InetSocketAddress> newAddressList = instancesToAddressList(instances);

                        // 更新本地缓存
                        serviceCache.put(serviceName, newAddressList);
                        log.info("检测到服务 [{}] 变化，更新本地缓存，当前实例数: {}", serviceName, newAddressList.size());
                    }
                }
            });

            subscribingServices.add(serviceName);
        }
    }

    // 辅助方法：把 Nacos 的 Instance 转成我们的 InetSocketAddress
    private List<InetSocketAddress> instancesToAddressList(List<Instance> instances) {
        List<InetSocketAddress> addressList = new ArrayList<>();
        if (instances != null) {
            for (Instance instance : instances) {
                // 只取健康的实例
                if (instance.isHealthy() && instance.isEnabled()) {
                    addressList.add(new InetSocketAddress(instance.getIp(), instance.getPort()));
                }
            }
        }
        return addressList;
    }
}