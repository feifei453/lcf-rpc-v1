package com.lcf.rpc.core;

import com.lcf.rpc.demo.consumer.HelloController;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.lcf.rpc")
public class SpringTestClient {
    public static void main(String[] args) {
        // 1. 启动 Spring 容器
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringTestClient.class);

        // 2. 取出 Bean 并测试
        HelloController controller = context.getBean(HelloController.class);
        controller.test();
    }
}