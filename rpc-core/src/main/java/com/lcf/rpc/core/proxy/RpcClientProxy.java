package com.lcf.rpc.core.proxy;

import com.lcf.rpc.common.enumeration.RpcMessageType;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.core.transport.NettyClient;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class RpcClientProxy implements InvocationHandler {

    private final NettyClient nettyClient;

    public RpcClientProxy(NettyClient nettyClient) {
        this.nettyClient = nettyClient;
    }

    /**
     * 获取代理对象 (工具方法)
     * @param clazz 接口的 Class 对象
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class<?>[]{clazz},
                this
        );
    }

    /**
     * 拦截逻辑：当用户调用接口方法时，会执行这里
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1. 构建 RPC 请求对象 (就像填快递单)
        RpcRequest rpcRequest = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName(method.getDeclaringClass().getName()) // 接口名
                .methodName(method.getName())                        // 方法名
                .parameters(args)                                    // 参数值
                .paramTypes(method.getParameterTypes())              // 参数类型
                .build();

        // 2. 包装成协议消息
        RpcMessage rpcMessage = RpcMessage.builder()
                .codec((byte) 1) // 目前写死 JDK 序列化
                .messageType(RpcMessageType.REQUEST.getCode())
                .data(rpcRequest)
                .build();

        // 3. 发送请求并阻塞等待结果
        // 这里就是体现 "同步调用" 的地方
        CompletableFuture<RpcResponse> future = nettyClient.sendRequest(rpcMessage);
        RpcResponse rpcResponse = future.get(); // 阻塞，直到 Netty 收到响应

        // 4. 检查响应状态并返回数据
        if (rpcResponse.getCode() == null || !rpcResponse.getCode().equals(200)) {
            throw new RuntimeException("RPC调用失败: " + rpcResponse.getMessage());
        }

        return rpcResponse.getData();
    }
}