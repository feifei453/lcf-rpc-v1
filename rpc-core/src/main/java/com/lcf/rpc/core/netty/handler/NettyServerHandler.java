package com.lcf.rpc.core.netty.handler;

import com.lcf.rpc.common.enumeration.RpcMessageType;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务端业务逻辑处理器
 */
@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcMessage> { // 泛型改成 RpcMessage

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        // 取出真正的 Request
        RpcRequest request = (RpcRequest) msg.getData();
        log.info("服务端收到请求: {}", request);

        // 构造响应
        RpcResponse response = RpcResponse.success("Hello, I am Protocol Server", request.getRequestId());

        // 封装协议包
        RpcMessage responseMsg = RpcMessage.builder()
                .codec((byte) 1)
                .messageType(RpcMessageType.RESPONSE.getCode()) // 标记为响应
                .data(response)
                .build();

        ctx.writeAndFlush(responseMsg);
    }
}