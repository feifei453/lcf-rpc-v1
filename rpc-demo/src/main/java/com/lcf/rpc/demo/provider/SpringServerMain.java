package com.lcf.rpc.demo.provider;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
// 1. "com.lcf.rpc" -> 扫描框架核心 (RpcServicePostProcessor 等)
// 2. "com.lcf.rpc.demo.provider" -> 扫描你的业务实现类
@ComponentScan(basePackages = {"com.lcf.rpc", "com.lcf.rpc.demo.provider"})
public class SpringServerMain {

    public static void main(String[] args) {
        // 启动 Spring 容器
        new AnnotationConfigApplicationContext(SpringServerMain.class);

        // RpcServicePostProcessor 会在容器启动后自动开启 Netty Server
        // 这里不需要写 server.start() 了
        System.out.println("✅ Spring RPC 服务端已启动...");

        // 阻塞主线程，防止 Spring 容器退出
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}