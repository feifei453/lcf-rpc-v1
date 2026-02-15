package com.lcf.rpc.core.transport;

import com.lcf.rpc.common.config.RpcProperties;
import com.lcf.rpc.common.extension.ExtensionLoader;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.core.netty.codec.RpcMessageDecoder;
import com.lcf.rpc.core.netty.codec.RpcMessageEncoder;
import com.lcf.rpc.core.netty.handler.NettyClientHandler;
import com.lcf.rpc.core.serialization.Serializer;
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
        // 1. 初始化资源 (只做一次)
        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
        // 2. 加载序列化器 (只做一次)
        String serializerKey = RpcProperties.getSerializer();
        this.serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(serializerKey);


        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 5秒没写数据，发送心跳
                        pipeline.addLast(new IdleStateHandler(0, 5, 0, TimeUnit.SECONDS));
                        // 使用你代码里的类名
                        pipeline.addLast(new RpcMessageEncoder(serializer));
                        pipeline.addLast(new RpcMessageDecoder(serializer));
                        // 传入 unprocessedRequests
                        pipeline.addLast(new NettyClientHandler(unprocessedRequests));
                    }
                });
    }

    @SneakyThrows
    public CompletableFuture<RpcResponse> sendRequest(RpcMessage rpcMessage, InetSocketAddress inetSocketAddress) {
        // 1. 复用连接！(不再每次都 create 了)
        Channel channel = getChannel(inetSocketAddress);

        if (!channel.isActive()) {
            channelCache.remove(inetSocketAddress.toString());
            throw new IllegalStateException("Channel is closed");
        }

        // 2. 准备 Future
        CompletableFuture<RpcResponse> resultFuture = new CompletableFuture<>();
        RpcRequest request = (RpcRequest) rpcMessage.getData();

        // 3. 注册到 unprocessedRequests
        unprocessedRequests.put(request.getRequestId(), resultFuture);

        // 4. 发送
        channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("请求发送成功: {}", request.getRequestId());
            } else {
                future.channel().close();
                resultFuture.completeExceptionally(future.cause());
                log.error("发送消息失败:", future.cause());
            }
        });

        return resultFuture;
    }

    /**
     * 核心改进：获取 Channel (带缓存 + 双重检查锁)
     */
    @SneakyThrows
    private Channel getChannel(InetSocketAddress inetSocketAddress) {
        String key = inetSocketAddress.toString();

        // 第一次检查：如果有且活跃，直接返回
        if (channelCache.containsKey(key)) {
            Channel channel = channelCache.get(key);
            if (channel != null && channel.isActive()) {
                return channel;
            }
            channelCache.remove(key);
        }

        // 加锁防止多线程并发创建多个连接
        synchronized (this) {
            // 第二次检查
            if (channelCache.containsKey(key)) {
                Channel channel = channelCache.get(key);
                if (channel != null && channel.isActive()) {
                    return channel;
                }
            }

            // 确实没有，才去连接
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
                throw new IllegalStateException("连接失败");
            }
        });
        // 阻塞等待连接建立，超时设为5秒
        return completableFuture.get(5, TimeUnit.SECONDS);
    }

    // 这是一个好习惯：应用关闭时释放资源
    public void close() {
        eventLoopGroup.shutdownGracefully();
    }
}