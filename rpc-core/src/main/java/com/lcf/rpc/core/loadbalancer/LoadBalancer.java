package com.lcf.rpc.core.loadbalancer;

import com.lcf.rpc.common.model.RpcRequest;
import java.util.List;

public interface LoadBalancer {
    /**
     * 从服务列表中选择一个
     * @param serviceAddresses 服务地址列表
     * @param rpcRequest 本次 RPC 请求的详细参数 (用于提取哈希键)
     * @return 选中的服务地址
     */
    String select(List<String> serviceAddresses, RpcRequest rpcRequest);
}