package com.lcf.rpc.core.loadbalancer;

import java.util.List;

/**
 * 负载均衡策略接口
 */
public interface LoadBalancer {
    /**
     * 从服务列表中选择一个
     * @param serviceAddresses 服务地址列表 (如 ["192.168.1.1:8080", "192.168.1.2:8080"])
     * @return 选中的地址
     */
    String select(List<String> serviceAddresses);
}