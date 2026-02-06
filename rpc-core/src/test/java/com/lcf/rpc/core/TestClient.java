package com.lcf.rpc.core;

import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.core.transport.NettyClient;

import java.util.UUID;

public class TestClient {
    public static void main(String[] args) {
        NettyClient client = new NettyClient();

        // 构建一个假请求
        RpcRequest request = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName("UserService")
                .methodName("getUser")
                .build();

        client.sendRequest(request);
    }
}
