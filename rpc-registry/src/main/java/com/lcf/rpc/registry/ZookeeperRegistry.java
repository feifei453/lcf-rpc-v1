package com.lcf.rpc.registry;

import com.lcf.rpc.common.config.RpcProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ZookeeperRegistry implements Registry {

    private final CuratorFramework client;
    private static final String ROOT_PATH = "/my-rpc";
    private final Map<String, List<InetSocketAddress>> serviceCache = new ConcurrentHashMap<>();
    private final Map<String, PathChildrenCache> watcherCache = new ConcurrentHashMap<>();

    public ZookeeperRegistry() {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        String connectString = RpcProperties.getRegistryAddress();

        this.client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(5000)
                .retryPolicy(retryPolicy)
                .namespace("lcf-rpc-framework")
                .build();
        this.client.start();
        log.info("Zookeeper 连接成功: {}", connectString);

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM 关闭，清理 Zookeeper 连接...");
            destroy();
        }));
    }

    @Override
    public void register(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            String servicePath = "/" + serviceName + "/" + getServiceAddress(inetSocketAddress);
            if (client.checkExists().forPath(servicePath) == null) {
                client.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL)
                        .forPath(servicePath);
                log.info("Zookeeper 注册服务成功: {}", servicePath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Zookeeper 注册服务失败", e);
        }
    }

    @Override
    public List<InetSocketAddress> lookupAll(String serviceName) {
        if (serviceCache.containsKey(serviceName)) {
            return serviceCache.get(serviceName);
        }
        List<InetSocketAddress> addresses = fetchAddressesFromZk(serviceName);
        serviceCache.put(serviceName, addresses);
        registerWatcher(serviceName);
        return addresses;
    }

    /**
     * 优雅关闭 Zookeeper 客户端
     */
    @Override
    public void destroy() {
        log.info("正在关闭 Zookeeper 客户端...");
        // 关闭所有监听器
        watcherCache.forEach((k, v) -> {
            try {
                v.close();
            } catch (Exception e) {
                log.warn("关闭 watcher 失败", e);
            }
        });

        // 关闭客户端连接
        // 这一步会断开连接，所有临时节点 (Ephemeral Nodes) 会自动被 ZK 删除
        if (client != null) {
            client.close();
        }
        log.info("Zookeeper 客户端已关闭");
    }

    // --- 辅助方法保持不变 ---

    private List<InetSocketAddress> fetchAddressesFromZk(String serviceName) {
        try {
            String servicePath = "/" + serviceName;
            List<String> children = client.getChildren().forPath(servicePath);
            List<InetSocketAddress> addressList = new ArrayList<>();
            for (String node : children) {
                String[] array = node.split(":");
                String host = array[0];
                int port = Integer.parseInt(array[1]);
                addressList.add(new InetSocketAddress(host, port));
            }
            return addressList;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void registerWatcher(String serviceName) {
        if (watcherCache.containsKey(serviceName)) {
            return;
        }
        String servicePath = "/" + serviceName;
        PathChildrenCache cache = new PathChildrenCache(client, servicePath, true);
        cache.getListenable().addListener((client, event) -> {
            if (event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED ||
                    event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED ||
                    event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED) {
                log.info("检测到服务 [{}] 节点变化，更新本地缓存", serviceName);
                List<InetSocketAddress> newAddresses = fetchAddressesFromZk(serviceName);
                serviceCache.put(serviceName, newAddresses);
            }
        });
        try {
            cache.start();
            watcherCache.put(serviceName, cache);
        } catch (Exception e) {
            log.error("监听器启动失败", e);
        }
    }

    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostString() + ":" + serverAddress.getPort();
    }
}