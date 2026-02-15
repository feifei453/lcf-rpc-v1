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

    // 本地缓存: serviceName -> list of addresses
    private final Map<String, List<InetSocketAddress>> serviceCache = new ConcurrentHashMap<>();

    // 记录已订阅的服务
    private final Set<String> subscribingServices = new HashSet<>();

    // ⚠️ 新增：记录本节点注册过的服务，用于下线时注销
    // Key: serviceName, Value: address
    private final Set<String> registeredServiceNames = ConcurrentHashMap.newKeySet();
    private InetSocketAddress localAddress;

    public NacosRegistry() {
        try {
            String registryAddress = RpcProperties.getRegistryAddress();
            this.namingService = NamingFactory.createNamingService(registryAddress);
            log.info("Nacos 连接成功: {}", registryAddress);

            // ⚠️ 核心：添加 JVM 关闭钩子，自动触发优雅下线
            // 当你按 Ctrl+C 或执行 kill (不带-9) 时，这个线程会被执行
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("JVM 正在关闭，开始执行 Nacos 优雅下线...");
                destroy();
            }));

        } catch (NacosException e) {
            throw new RuntimeException("连接 Nacos 失败", e);
        }
    }

    @Override
    public void register(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            namingService.registerInstance(serviceName, inetSocketAddress.getHostName(), inetSocketAddress.getPort());

            // 记录下来，以便 destroy 时注销
            registeredServiceNames.add(serviceName);
            this.localAddress = inetSocketAddress;

            log.info("Nacos 注册服务成功: {} -> {}", serviceName, inetSocketAddress);
        } catch (NacosException e) {
            throw new RuntimeException("Nacos 注册失败", e);
        }
    }

    @Override
    public List<InetSocketAddress> lookupAll(String serviceName) {
        if (serviceCache.containsKey(serviceName)) {
            return serviceCache.get(serviceName);
        }

        try {
            List<Instance> instances = namingService.selectInstances(serviceName, true);
            List<InetSocketAddress> addressList = instancesToAddressList(instances);

            serviceCache.put(serviceName, addressList);
            subscribeService(serviceName);

            return addressList;
        } catch (NacosException e) {
            throw new RuntimeException("Nacos 服务发现失败", e);
        }
    }

    /**
     * ⚠️ 核心实现：优雅下线
     * 主动注销服务，防止客户端继续向已下线的节点发请求
     */
    @Override
    public void destroy() {
        // 1. 注销所有已注册的服务
        if (!registeredServiceNames.isEmpty() && localAddress != null) {
            for (String serviceName : registeredServiceNames) {
                try {
                    namingService.deregisterInstance(serviceName, localAddress.getHostName(), localAddress.getPort());
                    log.info("Nacos 服务注销成功: {}", serviceName);
                } catch (NacosException e) {
                    log.error("Nacos 服务注销失败: {}", serviceName, e);
                }
            }
            registeredServiceNames.clear();
        }

        // 2. 关闭 Nacos 客户端资源 (可选)
        // namingService.shutDown(); // Nacos Client 1.x 版本可能没有 shutDown，视版本而定
    }

    private void subscribeService(String serviceName) throws NacosException {
        synchronized (subscribingServices) {
            if (subscribingServices.contains(serviceName)) {
                return;
            }

            log.info("开启 Nacos 监听: {}", serviceName);
            namingService.subscribe(serviceName, new EventListener() {
                @Override
                public void onEvent(Event event) {
                    if (event instanceof NamingEvent) {
                        NamingEvent namingEvent = (NamingEvent) event;
                        List<Instance> instances = namingEvent.getInstances();
                        List<InetSocketAddress> newAddressList = instancesToAddressList(instances);
                        serviceCache.put(serviceName, newAddressList);
                        log.info("服务 [{}] 变动，更新缓存，实例数: {}", serviceName, newAddressList.size());
                    }
                }
            });
            subscribingServices.add(serviceName);
        }
    }

    private List<InetSocketAddress> instancesToAddressList(List<Instance> instances) {
        List<InetSocketAddress> addressList = new ArrayList<>();
        if (instances != null) {
            for (Instance instance : instances) {
                if (instance.isHealthy() && instance.isEnabled()) {
                    addressList.add(new InetSocketAddress(instance.getIp(), instance.getPort()));
                }
            }
        }
        return addressList;
    }
}