package com.lcf.rpc.core;

import com.lcf.rpc.core.transport.NettyServer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.lcf.rpc") // 扫描 rpc-core 和 demo 下的包
public class SpringTestServer {
    public static void main(String[] args) {
        // 1. 启动 Spring 容器
        // 这一步会触发 SpringBeanPostProcessor，完成自动注册
        new AnnotationConfigApplicationContext(SpringTestServer.class);

        // 2. 启动 Netty 服务端 (阻塞主线程)
        // 在 V2.0 我们可以把 NettyServer 也做成一个 Bean，在 Spring 启动完成后自动 start
        new NettyServer(8080).start();
    }
}