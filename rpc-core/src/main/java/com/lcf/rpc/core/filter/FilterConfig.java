package com.lcf.rpc.core.filter;

import com.lcf.rpc.common.extension.ExtensionLoader;
import lombok.Getter;

@Getter
public class FilterConfig {

    private static final FilterChain clientBeforeChain = new FilterChain();
    private static final FilterChain clientAfterChain = new FilterChain();
    private static final FilterChain serviceBeforeChain = new FilterChain();
    private static final FilterChain serviceAfterChain = new FilterChain();

    // 静态块初始化：加载 SPI 文件中配置的过滤器
    static {
        // 这里为了简单演示，手动添加。
        // V2版本：你可以遍历 ExtensionLoader 加载所有实现类

        // 加载客户端过滤器
        clientBeforeChain.addFilter(new com.lcf.rpc.core.filter.client.ClientTokenFilter());

        // 加载服务端过滤器
        serviceBeforeChain.addFilter(new com.lcf.rpc.core.filter.server.ServiceTokenFilter());
    }

    public static FilterChain getClientBeforeChain() { return clientBeforeChain; }
    public static FilterChain getClientAfterChain() { return clientAfterChain; }
    public static FilterChain getServiceBeforeChain() { return serviceBeforeChain; }
    public static FilterChain getServiceAfterChain() { return serviceAfterChain; }
}