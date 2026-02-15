package com.lcf.rpc.demo.consumer;

import com.lcf.rpc.core.annotation.RpcReference;
import com.lcf.rpc.demo.api.HelloService;
import org.springframework.stereotype.Component;

@Component
public class HelloController {

    // 核心注解：@RpcReference 自动注入动态代理
    @RpcReference
    private HelloService helloService;

    public void test() throws InterruptedException {
        System.out.println(">>> 开始测试 RPC 调用...");

        // 循环调用，测试稳定性
        for (int i = 0; i < 10; i++) {
            try {
                String result = helloService.sayHello("SpringUser-" + i);
                System.out.println("✅ 调用成功: " + result);
            } catch (Exception e) {
                System.err.println("❌ 调用失败: " + e.getMessage());
            }
            Thread.sleep(1000); // 模拟业务间隔
        }
    }
}