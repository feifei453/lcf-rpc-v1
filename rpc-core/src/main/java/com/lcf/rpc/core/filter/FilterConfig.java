package com.lcf.rpc.core.filter;

import com.lcf.rpc.core.filter.client.ClientTokenFilter;
import com.lcf.rpc.core.filter.server.ServerRateLimitFilter; // 导入限流过滤器
import com.lcf.rpc.core.filter.server.ServiceTokenFilter;
import lombok.Getter;

@Getter
public class FilterConfig {

    private static final FilterChain clientBeforeChain = new FilterChain();
    private static final FilterChain clientAfterChain = new FilterChain();
    private static final FilterChain serviceBeforeChain = new FilterChain();
    private static final FilterChain serviceAfterChain = new FilterChain();

    // 静态块初始化：加载过滤器
    static {
        // --- 客户端链 ---
        // 客户端发送前，带上 Token
        clientBeforeChain.addFilter(new ClientTokenFilter());

        // --- 服务端链 (ServiceBeforeChain) ---
        // 1.  先限流 (保护系统)
        serviceBeforeChain.addFilter(new ServerRateLimitFilter());

        // 2.  后鉴权 (验证身份)
        serviceBeforeChain.addFilter(new ServiceTokenFilter());


    }

    public static FilterChain getClientBeforeChain() { return clientBeforeChain; }
    public static FilterChain getClientAfterChain() { return clientAfterChain; }
    public static FilterChain getServiceBeforeChain() { return serviceBeforeChain; }
    public static FilterChain getServiceAfterChain() { return serviceAfterChain; }
}