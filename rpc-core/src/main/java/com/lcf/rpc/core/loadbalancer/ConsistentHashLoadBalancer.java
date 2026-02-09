package com.lcf.rpc.core.loadbalancer;

import com.lcf.rpc.common.model.RpcRequest;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ConsistentHashLoadBalancer implements LoadBalancer {

    // 虚拟节点个数：每个真实节点对应 160 个虚拟节点
    private static final int VIRTUAL_NODE_SIZE = 160;

    // 缓存选择器：避免每次请求都重新构建哈希环
    private final Map<String, ConsistentHashSelector> selectors = new ConcurrentHashMap<>();

    @Override
    public String select(List<String> serviceAddresses, RpcRequest rpcRequest) {
        // 利用本次请求的参数作为 Hash Key
        // 这里默认取第一个参数作为哈希依据，你可以根据业务改为 userId 等
        Object[] parameters = rpcRequest.getParameters();
        String hashKey = (parameters != null && parameters.length > 0) ? parameters[0].toString() : "";

        // 生成这一组服务列表的唯一标识 (Identity Hash Code)
        // 如果服务列表变了（扩容/缩容），identityHashCode 也会变，触发重构哈希环
        int identityHashCode = System.identityHashCode(serviceAddresses);
        String serviceName = rpcRequest.getInterfaceName();

        ConsistentHashSelector selector = selectors.get(serviceName);

        // 如果还没有初始化，或者服务列表变了，就需要重新构建哈希环
        if (selector == null || selector.identityHashCode != identityHashCode) {
            selectors.put(serviceName, new ConsistentHashSelector(serviceAddresses, VIRTUAL_NODE_SIZE, identityHashCode));
            selector = selectors.get(serviceName);
        }

        return selector.select(hashKey);
    }

    /**
     * 内部类：具体的哈希环选择逻辑
     */
    static class ConsistentHashSelector {
        private final TreeMap<Long, String> virtualInvokers;
        private final int identityHashCode;

        public ConsistentHashSelector(List<String> invokers, int replicaNumber, int identityHashCode) {
            this.virtualInvokers = new TreeMap<>();
            this.identityHashCode = identityHashCode;

            for (String invoker : invokers) {
                // 为每个真实节点生成 replicaNumber 个虚拟节点
                for (int i = 0; i < replicaNumber / 4; i++) {
                    byte[] digest = md5(invoker + i);
                    for (int h = 0; h < 4; h++) {
                        long m = hash(digest, h);
                        virtualInvokers.put(m, invoker);
                    }
                }
            }
        }

        public String select(String rpcServiceKey) {
            byte[] digest = md5(rpcServiceKey);
            return selectForKey(hash(digest, 0));
        }

        private String selectForKey(long hash) {
            // 在环上找到 >= hash 的第一个节点
            Map.Entry<Long, String> entry = virtualInvokers.ceilingEntry(hash);
            if (entry == null) {
                // 如果没找到（超过了环的最大值），则取环上的第一个节点（回绕）
                entry = virtualInvokers.firstEntry();
            }
            return entry.getValue();
        }

        // --- 经典的 MD5 哈希算法 (保证分布均匀) ---
        private long hash(byte[] digest, int number) {
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[0 + number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }

        private byte[] md5(String value) {
            MessageDigest md5;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            md5.reset();
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            md5.update(bytes);
            return md5.digest();
        }
    }
}