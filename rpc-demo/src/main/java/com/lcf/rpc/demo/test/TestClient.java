package com.lcf.rpc.demo.test;

import com.lcf.rpc.core.proxy.RpcClientProxy;
import com.lcf.rpc.core.transport.NettyClient;
import com.lcf.rpc.demo.api.HelloService; // 引入你的接口

public class TestClient {
    public static void main(String[] args) throws InterruptedException {
        // 1. 启动客户端
        NettyClient client = new NettyClient();

        // 2. 创建代理
        RpcClientProxy proxyFactory = new RpcClientProxy(client);
        HelloService helloService = proxyFactory.getProxy(HelloService.class);

        System.out.println("开始循环调用远程服务...");

        // 3. ⚠️ 循环调用 10 次
        for (int i = 0; i < 10; i++) {
            // 调用
            String result = helloService.sayHello("LCF-" + i);

            // 打印结果
            System.out.println("第 " + (i + 1) + " 次调用成功: " + result);

            // 休息一下，方便观察日志
            Thread.sleep(2000);
        }
    }
}