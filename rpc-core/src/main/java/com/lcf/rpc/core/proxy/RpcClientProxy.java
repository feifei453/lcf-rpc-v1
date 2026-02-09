package com.lcf.rpc.core.proxy;

import com.lcf.rpc.common.enumeration.RpcMessageType;
import com.lcf.rpc.common.extension.ExtensionLoader;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.core.filter.FilterConfig;
import com.lcf.rpc.core.filter.FilterData;
import com.lcf.rpc.core.loadbalancer.ConsistentHashLoadBalancer;
import com.lcf.rpc.core.loadbalancer.LoadBalancer;
import com.lcf.rpc.core.transport.NettyClient;
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
    // 保持原样：使用 Zookeeper 和 一致性哈希
    private final Registry registry = ExtensionLoader.getExtensionLoader(Registry.class).getExtension("zookeeper");
    private final LoadBalancer loadBalancer = new ConsistentHashLoadBalancer();

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
        // 1. 构建请求 (逻辑保持不变)
        RpcRequest rpcRequest = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .parameters(args)
                .paramTypes(method.getParameterTypes())
                .build();

        // ⚠️ 核心新增 1：定义一个本次调用的“临时黑名单”
        // 用于记录在本次重试循环中失败过的节点地址
        List<String> failedNodeList = new ArrayList<>();

        // 2. 执行客户端前置过滤器 (逻辑保持不变)
        FilterData filterData = new FilterData(rpcRequest);
        FilterConfig.getClientBeforeChain().doFilter(filterData);
        rpcRequest.setAttachments(filterData.getAttachments());

        // 3. 构建协议消息 (逻辑保持不变)
        RpcMessage rpcMessage = RpcMessage.builder()
                .codec((byte) 2) // JSON
                .messageType(RpcMessageType.REQUEST.getCode())
                .data(rpcRequest)
                .build();

        // --- 重试机制参数 ---
        int retryCount = 5;     // 重试次数
        int timeout = 2000;     // 超时时间 (毫秒)

        String serviceName = method.getDeclaringClass().getName();
        Exception lastException = null;

        // 4. 开启重试循环
        for (int i = 0; i < retryCount; i++) {
            // 定义变量在 try 外面，以便 catch 块能获取到刚才选的是谁
            String selectedAddr = null;

            try {
                // 4.1 服务发现
                List<InetSocketAddress> addressList = registry.lookupAll(serviceName);

                // 4.2 转换地址格式
                List<String> stringList = new ArrayList<>();
                for (InetSocketAddress addr : addressList) {
                    stringList.add(addr.getHostString() + ":" + addr.getPort());
                }

                // ⚠️ 核心新增 2：从候选名单中【剔除】黑名单里的节点
                // 如果 ZK 还没来得及删死节点，我们自己手动在客户端屏蔽它
                stringList.removeAll(failedNodeList);

                if (stringList.isEmpty()) {
                    throw new RuntimeException("无可用服务节点 (重试耗尽或所有节点均失败)");
                }

                // 4.3 负载均衡选择
                // 现在传进去的 stringList 已经是干净的（不包含刚才失败的节点）
                selectedAddr = loadBalancer.select(stringList, rpcRequest);
                log.info("[第{}次调用] 负载均衡选择地址: {}", i + 1, selectedAddr);

                // 4.4 解析地址
                String[] array = selectedAddr.split(":");
                InetSocketAddress targetAddress = new InetSocketAddress(array[0], Integer.parseInt(array[1]));

                // 4.5 发送请求 (Netty 异步发送)
                CompletableFuture<RpcResponse> future = nettyClient.sendRequest(rpcMessage, targetAddress);

                // 4.6 等待响应 (带超时)
                RpcResponse rpcResponse = future.get(timeout, TimeUnit.MILLISECONDS);

                // 4.7 执行客户端后置过滤器 (逻辑保持不变)
                filterData.setResponse(rpcResponse);
                FilterConfig.getClientAfterChain().doFilter(filterData);

                // 4.8 检查结果
                if (rpcResponse.getCode() == 200) {
                    return rpcResponse.getData();
                } else {
                    throw new RuntimeException("服务端业务报错: " + rpcResponse.getMessage());
                }

            } catch (Exception e) {
                // ⚠️ 核心新增 3：捕获异常，将刚才选中的地址加入黑名单
                if (selectedAddr != null) {
                    log.warn("[第{}次调用] 失败: {}, 将地址 {} 加入临时黑名单", i + 1, e.getMessage(), selectedAddr);
                    failedNodeList.add(selectedAddr);
                } else {
                    log.warn("[第{}次调用] 失败: {}", i + 1, e.getMessage());
                }

                lastException = e;

                // 如果是最后一次重试，就不再 sleep 了，直接抛出
                if (i == retryCount - 1) {
                    break;
                }
            }
        }

        // 5. 重试耗尽，抛出最后一次的异常
        throw new RuntimeException("RPC调用失败，重试次数耗尽", lastException);
    }
}