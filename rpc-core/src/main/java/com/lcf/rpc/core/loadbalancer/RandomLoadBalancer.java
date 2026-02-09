package com.lcf.rpc.core.loadbalancer;

import com.lcf.rpc.common.model.RpcRequest;
import java.util.List;
import java.util.Random;

public class RandomLoadBalancer implements LoadBalancer {
    private final Random random = new Random();

    @Override
    public String select(List<String> serviceAddresses, RpcRequest rpcRequest) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            return null;
        }
        // 忽略 rpcRequest，继续随机逻辑
        int index = random.nextInt(serviceAddresses.size());
        return serviceAddresses.get(index);
    }
}