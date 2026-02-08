package com.lcf.rpc.core.proxy;

import com.lcf.rpc.common.enumeration.RpcMessageType;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.core.filter.FilterConfig;
import com.lcf.rpc.core.filter.FilterData;
import com.lcf.rpc.core.loadbalancer.LoadBalancer;
import com.lcf.rpc.core.loadbalancer.RandomLoadBalancer;
import com.lcf.rpc.core.transport.NettyClient;
import com.lcf.rpc.registry.NacosRegistry;
import com.lcf.rpc.registry.Registry;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class RpcClientProxy implements InvocationHandler {

    private final NettyClient nettyClient;
    private final Registry registry = new NacosRegistry();
    private final LoadBalancer loadBalancer = new RandomLoadBalancer();

    public RpcClientProxy(NettyClient nettyClient) {
        this.nettyClient = nettyClient;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class<?>[]{clazz},
                this
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1. 构建请求 (不变)
        RpcRequest rpcRequest = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .parameters(args)
                .paramTypes(method.getParameterTypes())
                .build();

        FilterData filterData = new FilterData(rpcRequest);
        FilterConfig.getClientBeforeChain().doFilter(filterData);

        // 把 Filter 处理过的数据（比如加了 Token 的 attachments）写回 rpcRequest
        rpcRequest.setAttachments(filterData.getAttachments());

        RpcMessage rpcMessage = RpcMessage.builder()
                .codec((byte) 2) // ⚠️ 确认一下这里，之前我们已经是 JSON(2) 了
                .messageType(RpcMessageType.REQUEST.getCode())
                .data(rpcRequest)
                .build();

        // --- 核心修改：超时重试机制 ---
        int retryCount = 3;     // 重试次数
        int timeout = 1000;     // 超时时间 (毫秒)，设短一点方便测试

        String serviceName = method.getDeclaringClass().getName();
        Exception lastException = null;

        // 2. 开启重试循环
        for (int i = 0; i < retryCount; i++) {
            try {
                // 3. 服务发现 & 负载均衡 (放在循环内，以便重试时能换一台机器)
                List<InetSocketAddress> addressList = registry.lookupAll(serviceName);

                // 转换地址格式给 LoadBalancer
                List<String> stringList = new ArrayList<>();
                for (InetSocketAddress addr : addressList) {
                    stringList.add(addr.getHostString() + ":" + addr.getPort());
                }

                // 选择地址
                String selectedAddr = loadBalancer.select(stringList);
                log.info("[第{}次调用] 负载均衡选择地址: {}", i + 1, selectedAddr);

                // 解析地址
                String[] array = selectedAddr.split(":");
                InetSocketAddress targetAddress = new InetSocketAddress(array[0], Integer.parseInt(array[1]));

                // 4. 发送请求
                CompletableFuture<RpcResponse> future = nettyClient.sendRequest(rpcMessage, targetAddress);

                // 5. ⚠️ 关键点：带超时的等待
                // 如果 timeout 时间内服务端没返回，这里会抛出 TimeoutException
                RpcResponse rpcResponse = future.get(timeout, TimeUnit.MILLISECONDS);
                // --- 🟢 插入点 2：执行 ClientAfter 链 (比如记录耗时) ---
                filterData.setResponse(rpcResponse);
                FilterConfig.getClientAfterChain().doFilter(filterData);
                // ------------------------------------------------------
                // 6. 检查结果
                if (rpcResponse.getCode() == 200) {
                    return rpcResponse.getData();
                } else {
                    throw new RuntimeException("服务端报错: " + rpcResponse.getMessage());
                }




            } catch (TimeoutException e) {
                log.warn("[第{}次调用] 请求超时，准备重试...", i + 1);
                lastException = e;
            } catch (Exception e) {
                log.warn("[第{}次调用] 请求异常: {}，准备重试...", i + 1, e.getMessage());
                lastException = e;
            }
        }

        // 7. 重试耗尽，抛出最后一次的异常
        throw new RuntimeException("RPC调用失败，重试次数耗尽", lastException);
    }
}