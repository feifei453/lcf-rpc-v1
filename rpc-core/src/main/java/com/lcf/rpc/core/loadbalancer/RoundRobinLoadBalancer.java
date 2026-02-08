package com.lcf.rpc.core.loadbalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinLoadBalancer implements LoadBalancer {

    // 原子计数器，保证线程安全
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public String select(List<String> serviceAddresses) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            return null;
        }
        // 核心逻辑：index % size
        // 使用 getAndIncrement 自增
        int currentIndex = index.getAndIncrement();
        // 防止溢出成负数（虽然很难）
        if (currentIndex < 0) {
            index.set(0);
            currentIndex = 0;
        }

        return serviceAddresses.get(currentIndex % serviceAddresses.size());
    }
}