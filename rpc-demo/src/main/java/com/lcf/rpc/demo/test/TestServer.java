package com.lcf.rpc.core;

import com.lcf.rpc.core.provider.ServiceProviderImpl;
import com.lcf.rpc.core.transport.NettyServer;
import com.lcf.rpc.demo.api.HelloService;
import com.lcf.rpc.demo.provider.HelloServiceImpl;
import com.lcf.rpc.registry.NacosRegistry;
import com.lcf.rpc.registry.Registry;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;


public class TestServer {
    public static void main(String[] args) {
        // 1. 本地注册 (为了 Handler 能反射调用)
        HelloServiceImpl helloService = new HelloServiceImpl();
        ServiceProviderImpl serviceProvider = new ServiceProviderImpl();
        serviceProvider.addServiceProvider(helloService);

        // 2. ⚠️ 新增：远程注册 (告诉 Nacos 我在哪)
        Registry registry = new NacosRegistry();

        // 获取本机 IP (在云服务器或Docker中可能需要特定配置，这里先用 getLocalHost)
        // 端口我们要和 NettyServer 保持一致
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            int port = 8080;

            // 注册 HelloService 接口
            // 注意：serviceProvider.addServiceProvider 里我们是用 getCanonicalName() 注册的
            // 所以这里也用接口的全限定名注册
            registry.register(HelloService.class.getCanonicalName(), new InetSocketAddress(host, port));

            // 3. 启动 Netty
            new NettyServer(port).start();

        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}