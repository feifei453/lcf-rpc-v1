package com.lcf.rpc.core;

import com.lcf.rpc.core.transport.NettyServer;

public class TestServer {
    public static void main(String[] args) {
        // 启动服务端，监听 8080 端口
        new NettyServer(8080).start();
    }
}