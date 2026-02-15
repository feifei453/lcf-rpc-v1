package com.lcf.rpc.registry;

import java.net.InetSocketAddress;
import java.util.List;

public interface Registry {

    /**
     * 注册服务
     * @param serviceName 服务名称
     * @param inetSocketAddress 提供者的地址
     */
    void register(String serviceName, InetSocketAddress inetSocketAddress);

    /**
     * 发现服务
     * @param serviceName 服务名称
     * @return 服务地址列表
     */
    List<InetSocketAddress> lookupAll(String serviceName);

    /**
     * 销毁/关闭注册中心 (优雅下线)
     * 作用：主动注销服务，关闭连接资源
     */
    void destroy();
}