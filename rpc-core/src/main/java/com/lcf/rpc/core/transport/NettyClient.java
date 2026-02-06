package com.lcf.rpc.core.transport;

import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.core.netty.codec.RpcMessageDecoder;
import com.lcf.rpc.core.netty.codec.RpcMessageEncoder;
import com.lcf.rpc.core.netty.handler.NettyClientHandler;
import com.lcf.rpc.core.serialization.JdkSerializer;
import com.lcf.rpc.core.serialization.Serializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class NettyClient {

    private final UnprocessedRequests unprocessedRequests;

    public NettyClient() {
        this.unprocessedRequests = new UnprocessedRequests();
    }

    // ⚠️ 修改点1：返回值改为 CompletableFuture<RpcResponse>
    public CompletableFuture<RpcResponse> sendRequest(RpcMessage rpcMessage) {
        CompletableFuture<RpcResponse> resultFuture = new CompletableFuture<>();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            Serializer serializer = new JdkSerializer();
                            ch.pipeline().addLast(new RpcMessageEncoder(serializer));
                            ch.pipeline().addLast(new RpcMessageDecoder(serializer));
                            // ⚠️ 修改点2：Handler 需要传入 unprocessedRequests，以便收到消息时处理
                            ch.pipeline().addLast(new NettyClientHandler(unprocessedRequests));
                        }
                    });

            ChannelFuture channelFuture = bootstrap.connect("127.0.0.1", 8080).sync();

            // ⚠️ 修改点3：在发送前，先把 future 存起来
            // 注意：这里需要从 rpcMessage 里拿出 requestId。
            // 假设我们约定 RpcRequest 在 data 字段里 (虽然 data 是 Object，强转一下)
            // (稍微有点丑陋，V2版本我们会优化这里)
            com.lcf.rpc.common.model.RpcRequest req = (com.lcf.rpc.common.model.RpcRequest) rpcMessage.getData();
            unprocessedRequests.put(req.getRequestId(), resultFuture);

            Channel channel = channelFuture.channel();
            channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("请求发送成功: {}", req.getRequestId());
                } else {
                    future.cause().printStackTrace();
                    resultFuture.completeExceptionally(future.cause());
                }
            });

            // ⚠️ 注意：这里不能关闭 channel，也不能关闭 group！
            // 因为我们要等待服务器发回响应。如果关了，谁来接收？

        } catch (InterruptedException e) {
            resultFuture.completeExceptionally(e);
            Thread.currentThread().interrupt();
        }

        return resultFuture;
    }
}