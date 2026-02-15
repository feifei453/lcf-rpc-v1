package com.lcf.rpc.core.filter.server;

import com.google.common.util.concurrent.RateLimiter;
import com.lcf.rpc.common.config.RpcProperties;
import com.lcf.rpc.core.filter.Filter;
import com.lcf.rpc.core.filter.FilterData;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端限流过滤器
 * 作用：保护服务端不被突发流量打挂
 */
@Slf4j
public class ServerRateLimitFilter implements Filter {

    // 缓存每个接口的限流器 (InterfaceName -> RateLimiter)
    private static final Map<String, RateLimiter> LIMITER_MAP = new ConcurrentHashMap<>();

    //  设定限流速率：每秒只允许 10 个请求 (QPS = 10)
    // 生产环境这个值通常配置在 Nacos 里动态调整
    private static final double DEFAULT_QPS = 10.0;

    @Override
    public void doFilter(FilterData filterData) {
        // 直接从 filterData 获取接口名
        String interfaceName = filterData.getInterfaceName();

        // 1. 获取或创建该接口的限流器 (懒加载)
        RateLimiter rateLimiter = LIMITER_MAP.computeIfAbsent(interfaceName, k -> {
            double qps = RpcProperties.getRateLimitQps();
            log.info("... QPS={}", qps);
            return RateLimiter.create(qps);
        });

        // 2. 尝试获取令牌 (非阻塞)
        // 如果拿不到令牌，立即返回 false，不让业务线程等待
        if (!rateLimiter.tryAcquire()) {
            log.warn("接口 {} 流量超限，触发限流保护！", interfaceName);
            // 抛出异常，这将直接中断 Filter 链，NettyServerHandler 会捕获并返回错误信息给客户端
            throw new RuntimeException("Server is busy: Rate limit exceeded");
        }

        // 拿到令牌，放行，自然进入下一个 Filter
    }
}