package com.lcf.rpc.demo.provider;

import com.lcf.rpc.demo.api.HelloService;

public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "你好, " + name + "! (来自服务端的消息)";
    }
}