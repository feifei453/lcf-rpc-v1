package com.lcf.rpc.demo.api;

public interface HelloService {
    String sayHello(String name) throws InterruptedException;
}