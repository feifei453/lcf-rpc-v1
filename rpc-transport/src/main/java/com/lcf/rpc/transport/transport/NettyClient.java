package com.lcf.rpc.transport.transport;

import com.lcf.rpc.common.config.RpcProperties;
import com.lcf.rpc.common.extension.ExtensionLoader;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.serialization.Serializer;
import com.lcf.rpc.transport.netty.codec.RpcMessageDecoder;
import com.lcf.rpc.transport.netty.codec.RpcMessageEncoder;
import com.lcf.rpc.transport.netty.handler.NettyClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyClient {

    private final UnprocessedRequests unprocessedRequests;
    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final Serializer serializer;

    // 连接缓存 (Key: "ip:port", Value: Channel)
    private final Map<String, Channel> channelCache = new ConcurrentHashMap<>();

    public NettyClient() {
        this.unprocessedRequests = new UnprocessedRequests();
        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();

        String serializerKey = RpcProperties.getSerializer();
        this.serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(serializerKey);

        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        //配置心跳时间
                        // readerIdleTime = 15s (15秒没读到数据就触发 READER_IDLE -> 关闭连接)
                        // writerIdleTime = 5s  (5秒没写数据就触发 WRITER_IDLE -> 发送 PING)
                        // allIdleTime = 0 (不关心)
                        pipeline.addLast(new IdleStateHandler(15, 5, 0, TimeUnit.SECONDS));

                        pipeline.addLast(new RpcMessageEncoder(serializer));
                        pipeline.addLast(new RpcMessageDecoder(serializer));
                        pipeline.addLast(new NettyClientHandler(unprocessedRequests));
                    }
                });
    }

    @SneakyThrows
    public CompletableFuture<RpcResponse> sendRequest(RpcMessage rpcMessage, InetSocketAddress inetSocketAddress) {
        // 1. 获取连接 (复用或创建)
        Channel channel = getChannel(inetSocketAddress);

        if (!channel.isActive()) {
            channelCache.remove(inetSocketAddress.toString());
            throw new IllegalStateException("Failed to send request: Channel " + inetSocketAddress + " is closed");
        }

        // 2. 注册 Future，等待响应
        CompletableFuture<RpcResponse> resultFuture = new CompletableFuture<>();
        RpcRequest request = (RpcRequest) rpcMessage.getData();
        unprocessedRequests.put(request.getRequestId(), resultFuture);

        // 3. 发送消息
        channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.debug("请求发送成功: {}", request.getRequestId());
            } else {
                future.channel().close();
                resultFuture.completeExceptionally(future.cause());
                log.error("发送消息失败:", future.cause());
            }
        });

        return resultFuture;
    }

    /**
     * 获取 Channel (优化版)
     */
    @SneakyThrows
    private Channel getChannel(InetSocketAddress inetSocketAddress) {
        String key = inetSocketAddress.toString();

        // 1. 快速检查缓存
        if (channelCache.containsKey(key)) {
            Channel channel = channelCache.get(key);
            if (channel != null && channel.isActive()) {
                return channel;
            }
            // 如果连接不活跃，移除并重新连接
            channelCache.remove(key);
        }

        // 2. 创建连接 (加锁防止并发创建)
        // 注意：这里仍然使用 synchronized(this) 避免同一个目标地址瞬间建立多条连接
        // 在生产级优化中，可以用 ConcurrentHashMap<String, Future<Channel>> 来细粒度锁，但当前足以
        synchronized (this) {
            // 双重检查
            if (channelCache.containsKey(key)) {
                Channel channel = channelCache.get(key);
                if (channel != null && channel.isActive()) {
                    return channel;
                }
            }

            // 真正建立连接
            Channel channel = doConnect(inetSocketAddress);
            channelCache.put(key, channel);
            return channel;
        }
    }

    @SneakyThrows
    private Channel doConnect(InetSocketAddress inetSocketAddress) {
        CompletableFuture<Channel> completableFuture = new CompletableFuture<>();
        bootstrap.connect(inetSocketAddress).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("客户端连接成功: {}", inetSocketAddress.toString());
                completableFuture.complete(future.channel());
            } else {
                throw new IllegalStateException("连接失败: " + inetSocketAddress);
            }
        });
        // 5秒超时
        return completableFuture.get(5, TimeUnit.SECONDS);
    }

    public void close() {
        eventLoopGroup.shutdownGracefully();
    }
}