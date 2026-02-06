package com.lcf.rpc.core.transport;

import com.lcf.rpc.common.model.RpcResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 未处理的请求容器
 * 作用：存放发出去但还没收到响应的请求
 */
public class UnprocessedRequests {

    // Key: RequestId, Value: CompletableFuture (用来等待结果)
    private static final Map<String, CompletableFuture<RpcResponse>> UNPROCESSED_RESPONSE_FUTURES = new ConcurrentHashMap<>();

    /**
     * 放入一个未处理的请求
     */
    public void put(String requestId, CompletableFuture<RpcResponse> future) {
        UNPROCESSED_RESPONSE_FUTURES.put(requestId, future);
    }

    /**
     * 收到响应后，完成对应的 Future
     */
    public void complete(RpcResponse rpcResponse) {
        // 移除并获取对应的 Future
        CompletableFuture<RpcResponse> future = UNPROCESSED_RESPONSE_FUTURES.remove(rpcResponse.getRequestId());

        if (future != null) {
            future.complete(rpcResponse);
        } else {
            System.out.println("警告：收到在这个节点上未找到上下文的响应，可能是超时或重复响应。ID: " + rpcResponse.getRequestId());
        }
    }
}