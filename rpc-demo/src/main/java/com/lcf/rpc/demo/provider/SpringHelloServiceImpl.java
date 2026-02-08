package com.lcf.rpc.demo.provider;

import com.lcf.rpc.core.annotation.RpcService;
import com.lcf.rpc.demo.api.HelloService;

@RpcService
public class SpringHelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) throws InterruptedException {
        return "Hello, " + name + " (From Spring Service)";

    }
}