package com.lcf.rpc.registry;

import java.net.InetSocketAddress;
import java.util.List;

public interface Registry {

    /**
     * 注册服务
     * @param serviceName 服务名称 (如 com.lcf.HelloService)
     * @param inetSocketAddress 提供者的地址 (IP:Port)
     */
    void register(String serviceName, InetSocketAddress inetSocketAddress);

    /**
     * 发现服务 (这里简化一下，只返回一个地址。V2版本再做负载均衡返回 List)
     * @param serviceName 服务名称
     * @return 服务地址
     */
    List<InetSocketAddress> lookupAll(String serviceName);
}