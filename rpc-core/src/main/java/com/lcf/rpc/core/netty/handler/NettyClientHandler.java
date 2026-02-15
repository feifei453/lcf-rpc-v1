package com.lcf.rpc.core.netty.handler;

import com.lcf.rpc.common.enumeration.RpcMessageType;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.core.transport.UnprocessedRequests;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private final UnprocessedRequests unprocessedRequests;

    public NettyClientHandler(UnprocessedRequests unprocessedRequests) {
        this.unprocessedRequests = unprocessedRequests;
    }
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            // 如果 5 秒没写数据，发送心跳
            if (state == IdleState.WRITER_IDLE) {
                log.info("客户端空闲，发送心跳包 [PING]");
                RpcMessage ping = RpcMessage.builder()
                        .codec((byte) 2) // 假设用 JSON
                        .messageType(RpcMessageType.HEARTBEAT_REQUEST.getCode())
                        .data("PING")
                        .build();
                ctx.writeAndFlush(ping);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        byte messageType = msg.getMessageType();

        //  处理心跳响应
        if (messageType == RpcMessageType.HEARTBEAT_RESPONSE.getCode()) {
            log.info("收到服务端心跳响应 [PONG]");
            return;
        }
        log.info("收到服务端消息: {}", msg);

        // 1. 拿到响应对象
        RpcResponse response = (RpcResponse) msg.getData();

        // 2. 核心步骤：找到之前的 Future 并 complete 它
        unprocessedRequests.complete(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端异常", cause);
        ctx.close();
    }
}