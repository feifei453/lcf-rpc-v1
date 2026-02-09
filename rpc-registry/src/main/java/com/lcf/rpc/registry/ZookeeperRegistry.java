package com.lcf.rpc.registry;

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

    // Zookeeper 客户端
    private final CuratorFramework client;
    // 根路径
    private static final String ROOT_PATH = "/my-rpc";
    // 缓存服务
    private final Map<String, List<InetSocketAddress>> serviceCache = new ConcurrentHashMap<>();
    // 记录已经注册了监听器的服务，防止重复注册
    private final Map<String, PathChildrenCache> watcherCache = new ConcurrentHashMap<>();
    public ZookeeperRegistry() {
        // 1. 连接策略：重试 3 次，每次间隔 1000ms
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);

        // 2. 创建客户端
        // ⚠️ 这里填你虚拟机的 IP:Port
        String connectString = "192.168.200.130:2181";

        this.client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(5000) // 会话超时
                .retryPolicy(retryPolicy)
                .namespace("lcf-rpc-framework") // 命名空间，所有节点都会在这个目录下
                .build();

        // 3. 启动客户端
        this.client.start();
        log.info("Zookeeper 连接成功: {}", connectString);
    }

    @Override
    public void register(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            // 构建路径：/服务名/IP:Port
            String servicePath = "/" + serviceName + "/" + getServiceAddress(inetSocketAddress);

            // 创建临时节点 (Ephemeral Node)
            // 临时节点的特性：客户端断开连接（如服务挂了），节点自动删除
            if (client.checkExists().forPath(servicePath) == null) {
                client.create()
                        .creatingParentsIfNeeded() // 如果父节点不存在，自动创建
                        .withMode(CreateMode.EPHEMERAL) // ⚠️ 关键：临时节点
                        .forPath(servicePath);
                log.info("Zookeeper 注册服务成功: {}", servicePath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Zookeeper 注册服务失败", e);
        }
    }

    @Override
    public List<InetSocketAddress> lookupAll(String serviceName) {
        // ⚠️ 核心升级 2：优先读缓存
        if (serviceCache.containsKey(serviceName)) {
            return serviceCache.get(serviceName);
        }

        // 缓存里没有，去 ZK 查，并开启监听
        List<InetSocketAddress> addresses = fetchAddressesFromZk(serviceName);

        // 放入缓存
        serviceCache.put(serviceName, addresses);

        // 开启监听
        registerWatcher(serviceName);

        return addresses;
    }
    // --- 辅助方法：从 ZK 拉取最新地址 ---
    private List<InetSocketAddress> fetchAddressesFromZk(String serviceName) {
        try {
            String servicePath = "/" + serviceName;
            List<String> children = client.getChildren().forPath(servicePath);

            List<InetSocketAddress> addressList = new ArrayList<>();
            for (String node : children) {
                // node 格式: 192.168.x.x:8080
                String[] array = node.split(":");
                String host = array[0];
                int port = Integer.parseInt(array[1]);
                addressList.add(new InetSocketAddress(host, port));
            }
            return addressList;
        } catch (Exception e) {
            // 如果节点还没创建（比如服务刚起还没来得及注册），返回空列表而不是报错
            return new ArrayList<>();
        }
    }

    // --- 核心升级 3：注册监听器 (Watcher) ---
    private void registerWatcher(String serviceName) {
        if (watcherCache.containsKey(serviceName)) {
            return;
        }

        String servicePath = "/" + serviceName;
        // PathChildrenCache 专门监听子节点的变化 (新增、删除、更新)
        PathChildrenCache cache = new PathChildrenCache(client, servicePath, true);

        cache.getListenable().addListener(new PathChildrenCacheListener() {
            @Override
            public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                // 只要子节点有变动，就重新拉取整个列表更新缓存
                // 这样写虽然简单粗暴，但绝对保证一致性，且实现简单
                if (event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED ||
                        event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED ||
                        event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED) {

                    log.info("检测到服务 [{}] 节点变化，更新本地缓存...", serviceName);
                    List<InetSocketAddress> newAddresses = fetchAddressesFromZk(serviceName);
                    serviceCache.put(serviceName, newAddresses);
                    log.info("本地缓存已更新，最新地址数量: {}", newAddresses.size());
                }
            }
        });

        try {
            cache.start();
            watcherCache.put(serviceName, cache);
        } catch (Exception e) {
            log.error("监听器启动失败", e);
        }
    }



    // 辅助方法：把地址转成字符串 "ip:port"
    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostString() + ":" + serverAddress.getPort();
    }
}