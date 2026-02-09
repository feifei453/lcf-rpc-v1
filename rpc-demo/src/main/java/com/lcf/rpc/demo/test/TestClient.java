package com.lcf.rpc.demo.test;

import com.lcf.rpc.core.proxy.RpcClientProxy;
import com.lcf.rpc.core.transport.NettyClient;
import com.lcf.rpc.demo.api.HelloService; // 引入你的接口

import java.util.Random;

public class TestClient {
    public static void main(String[] args) throws InterruptedException {
        // 1. 启动客户端
        NettyClient client = new NettyClient();

        // 2. 创建代理
        RpcClientProxy proxyFactory = new RpcClientProxy(client);
        HelloService helloService = proxyFactory.getProxy(HelloService.class);

        System.out.println("开始循环调用远程服务...");

        // 模拟 5 个不同的用户 ID
        String[] users = {"User1", "User2", "User3", "User4", "User5"};

        // 3. ⚠️ 循环调用 10 次
        for (int i = 0; i < 10000; i++) {
            // 调用
            String user = users[new Random().nextInt(users.length)];

            // 调用 RPC
            String result = helloService.sayHello(user);
            // 这里的 user 就是 rpcRequest.parameters[0]，也是我们的 Hash Key

            // 打印结果
            System.out.println("第 " + (i + 1) + " 次调用成功: " + result);

            // 休息一下，方便观察日志
            Thread.sleep(1000);
        }
    }
}