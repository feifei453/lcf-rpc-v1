package com.lcf.rpc.core;

import com.lcf.rpc.common.enumeration.RpcMessageType;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.core.transport.NettyClient;

import java.util.UUID;

public class TestClient {
    public static void main(String[] args) {
        NettyClient client = new NettyClient();

        // 构建一个假请求
        // 1. 构建业务请求
        RpcRequest request = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName("UserService")
                .methodName("getUser")
                .parameters(new Object[]{"lcf"})
                .paramTypes(new Class[]{String.class})
                .build();

// 2. 构建协议包
        RpcMessage rpcMessage = RpcMessage.builder()
                .codec((byte) 1) // 1代表 JDK序列化
                .messageType(RpcMessageType.REQUEST.getCode()) // 标记这是“请求”
                .data(request)
                .build();

// 3. 发送
        client.sendRequest(rpcMessage);

    }
}
