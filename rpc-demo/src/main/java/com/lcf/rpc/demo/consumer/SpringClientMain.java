package com.lcf.rpc.demo.consumer;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
// ⚠️ 关键点：
// 1. "com.lcf.rpc" -> 扫描框架核心 (RpcReferencePostProcessor 等)
// 2. "com.lcf.rpc.demo.consumer" -> 扫描 HelloController
@ComponentScan(basePackages = {"com.lcf.rpc", "com.lcf.rpc.demo.consumer"})
public class SpringClientMain {

    public static void main(String[] args) throws InterruptedException {
        // 1. 启动 Spring 容器
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringClientMain.class);

        // 2. 取出业务 Bean
        HelloController controller = context.getBean(HelloController.class);

        // 3. 执行测试方法
        controller.test();
    }
}