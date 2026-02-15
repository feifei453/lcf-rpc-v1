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

            if (state == IdleState.WRITER_IDLE) {
                // 1. 写空闲：发送心跳包保活
                log.debug("客户端空闲，发送心跳包 [PING]");
                RpcMessage ping = RpcMessage.builder()
                        .codec((byte) 1) // codec 没所谓，心跳包不走序列化
                        .messageType(RpcMessageType.HEARTBEAT_REQUEST.getCode())
                        .data("PING")
                        .build();
                ctx.writeAndFlush(ping);

            } else if (state == IdleState.READER_IDLE) {
                // 2. 读空闲：断线重连机制的核心
                // 如果超过一定时间（如 15s）没收到服务端任何响应，说明连接假死
                log.warn("长时间未收到服务端响应，连接假死，强制关闭: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        byte messageType = msg.getMessageType();

        // 处理心跳响应
        if (messageType == RpcMessageType.HEARTBEAT_RESPONSE.getCode()) {
            log.debug("收到服务端心跳响应 [PONG]");
            return;
        }

        log.info("收到服务端消息: {}", msg);

        // 正常响应处理
        RpcResponse response = (RpcResponse) msg.getData();
        unprocessedRequests.complete(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端连接异常: {}", cause.getMessage());
        ctx.close();
    }
}