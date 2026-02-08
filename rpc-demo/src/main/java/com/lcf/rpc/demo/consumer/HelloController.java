package com.lcf.rpc.demo.consumer;

import com.lcf.rpc.core.annotation.RpcReference;
import com.lcf.rpc.demo.api.HelloService;
import org.springframework.stereotype.Component;

@Component
public class HelloController {

    @RpcReference // ⚠️ 自动注入代理对象
    private HelloService helloService;

    public void test() throws InterruptedException {
        String result = helloService.sayHello("Spring-LCF");
        System.out.println("调用结果: " + result);
    }
}