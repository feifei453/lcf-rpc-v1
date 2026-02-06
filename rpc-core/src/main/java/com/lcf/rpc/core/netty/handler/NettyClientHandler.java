package com.lcf.rpc.core.netty.handler;

import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.core.transport.UnprocessedRequests;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private final UnprocessedRequests unprocessedRequests;

    public NettyClientHandler(UnprocessedRequests unprocessedRequests) {
        this.unprocessedRequests = unprocessedRequests;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        log.info("收到服务端消息: {}", msg);

        // 1. 拿到响应对象
        RpcResponse response = (RpcResponse) msg.getData();

        // 2. ⚠️ 核心步骤：找到之前的 Future 并 complete 它
        unprocessedRequests.complete(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端异常", cause);
        ctx.close();
    }
}