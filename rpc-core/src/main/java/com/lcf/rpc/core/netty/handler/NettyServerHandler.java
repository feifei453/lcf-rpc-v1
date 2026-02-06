package com.lcf.rpc.core.netty.handler;

import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务端业务逻辑处理器
 */
@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
        // 1. 打印接收到的信息
        log.info("服务端收到请求: [RequestId: {}, Method: {}]", request.getRequestId(), request.getMethodName());

        // 2. 这里本该执行真正的业务逻辑（反射调用 impl 实现类）
        // 但现在我们先模拟一个成功的响应
        RpcResponse response = RpcResponse.success("Hello from Server! 我收到了你的消息", request.getRequestId());

        // 3. 写回响应
        // 注意：writeAndFlush 会走 Encoder，把 RpcResponse 变成字节发回给客户端
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("服务端处理异常", cause);
        ctx.close();
    }
}