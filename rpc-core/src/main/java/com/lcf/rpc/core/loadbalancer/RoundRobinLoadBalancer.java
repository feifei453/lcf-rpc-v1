package com.lcf.rpc.core.loadbalancer;

import com.lcf.rpc.common.model.RpcRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public String select(List<String> serviceAddresses, RpcRequest rpcRequest) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            return null;
        }
        // 忽略 rpcRequest，继续轮询逻辑
        int currentIndex = index.getAndIncrement();
        if (currentIndex < 0) {
            index.set(0);
            currentIndex = 0;
        }
        return serviceAddresses.get(currentIndex % serviceAddresses.size());
    }
}