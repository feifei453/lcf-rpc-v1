package com.lcf.rpc.core;

import com.lcf.rpc.core.proxy.RpcClientProxy;
import com.lcf.rpc.core.transport.NettyClient;
import com.lcf.rpc.demo.api.HelloService; // 引入你的接口

public class TestClient {
    public static void main(String[] args) {
        // 1. 启动 Netty 客户端
        NettyClient client = new NettyClient();

        // 2. 创建代理工厂
        RpcClientProxy proxyFactory = new RpcClientProxy(client);

        // 3. 获取接口的代理对象 (这一步最神奇)
        // 客户端里并没有 HelloServiceImpl 的代码，只有一个接口
        HelloService helloService = proxyFactory.getProxy(HelloService.class);

        // 4. 像调用本地方法一样调用远程服务
        System.out.println("开始调用远程服务...");

        // --- 这一行代码会触发 RpcClientProxy.invoke ---
        String result = helloService.sayHello("LCF");

        System.out.println("调用结果: " + result);
    }
}